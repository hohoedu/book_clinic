package com.hohoedu.book_clinic.pass;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.utils.KstClock;
import com.hohoedu.book_clinic.pass._dto.PassRespDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 이용권 도메인 — 발급 / 차감 / 회수 / 잔여조회.
 *
 * [왜 결제와 분리돼 있나] 프로그램비를 걷는 방법이 학생에 따라 다르다.
 *   · 책방만 이용 → 앱에서 학부모가 이니시스로 직접 결제 (source=PG)
 *   · 서당 병행   → 교재비에 얹어 전월 20일 일괄 청구 (source=SEODANG, all_pass 소관)
 * 두 경우 모두 "몇 회 남았나"는 똑같이 필요하지만 서당 학생은 이 시스템에 결제 행 자체가
 * 없다. 그래서 이용권을 결제에서 떼어냈고, 그 덕에 출석 차감 코드에는 결제 방식 분기가 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PassService {

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    public static final String SOURCE_PG = "PG";
    public static final String SOURCE_SEODANG = "SEODANG";
    public static final String SOURCE_FREE = "FREE";

    private final PassRepository passRepository;

    /**
     * 이용권 발급. PG 승인 성공 직후 결제 트랜잭션 안에서 호출되므로 별도 트랜잭션을 열지 않는다
     * (여기서 실패하면 결제도 함께 롤백되고 망취소로 이어져야 한다).
     */
    public void grant(String studentId, String centerCode, int productId, String serviceCode,
                      String source, String refNo, int totalCount) {
        passRepository.insertPass(studentId, centerCode, productId, serviceCode, source, refNo,
                KstClock.today().format(YM), totalCount);
    }

    /**
     * 출석 1회 차감.
     *
     * 같은 날 재입실은 차감하지 않는다. 학생이 로그아웃했다 다시 들어오거나 화면을 새로고침하는
     * 일이 흔한데 그때마다 횟수가 깎이면 안 되기 때문이다. UNIQUE 제약이 최종 방어선이지만,
     * 예외로 흐름이 끊기지 않도록 여기서 먼저 확인한다.
     *
     * @return 차감 후 남은 횟수. 쓸 수 있는 이용권이 없으면 차감하지 않고 -1
     */
    @Transactional
    public int consume(String studentId, String serviceCode, Integer sessionId) {
        LocalDate today = KstClock.today();

        if (passRepository.existsTodayUse(studentId, today)) {
            return passRepository.sumRemain(studentId, serviceCode);
        }

        PassRespDTO.PassDTO pass = passRepository.findUsablePass(studentId, serviceCode);
        if (pass == null) {
            log.info("[이용권] 잔여 없음 — studentId={}, service={}", studentId, serviceCode);
            return -1;
        }

        // 조회와 갱신 사이에 다른 요청이 먼저 깎았으면 0행이 된다. 그 경우 이번 요청은 차감하지 않는다.
        if (passRepository.decrementRemain(pass.getPassId()) == 0) {
            log.warn("[이용권] 차감 경합 — passId={}, studentId={}", pass.getPassId(), studentId);
            return passRepository.sumRemain(studentId, serviceCode);
        }

        passRepository.insertUse(pass.getPassId(), studentId, sessionId, today);
        return passRepository.sumRemain(studentId, serviceCode);
    }

    /** 이 학생이 이 서비스에 쓸 수 있는 총 잔여 횟수 */
    public int remain(String studentId, String serviceCode) {
        return passRepository.sumRemain(studentId, serviceCode);
    }

    /** 결제/청구 건으로 발급된 이용권 (없으면 null) */
    public PassRespDTO.PassDTO findByRef(String source, String refNo) {
        return passRepository.findByRef(source, refNo);
    }

    /** 이 이용권에서 실제 차감된 횟수 — 환불 규정의 "몇 회 썼는가" */
    public int usedCount(int passId) {
        return passRepository.countUse(passId);
    }

    /**
     * 이용권 회수 — 환불이 확정되면 남은 횟수를 거둬들인다.
     * 이미 회수된 건에 또 들어오면 UPDATE가 0행이 되는데, 환불 재시도에서 정상적으로 생기는
     * 상황이라 예외로 올리지 않고 그대로 흘려보낸다.
     */
    @Transactional
    public void revoke(int passId) {
        passRepository.revoke(passId);
    }

    /**
     * 서당 일괄청구분 발급 — all_pass가 청구를 확정할 때 호출한다.
     * 같은 청구 건으로 두 번 들어오면 이용권이 두 장 생기므로 ref_no로 중복을 막는다.
     */
    @Transactional
    public void grantFromSeodang(String studentId, String centerCode, int productId, String serviceCode,
                                 String billId, int totalCount) {
        if (billId == null || billId.isBlank()) {
            throw new Exception400("서당 청구 식별자(bill_id)가 없습니다.");
        }
        if (passRepository.findByRef(SOURCE_SEODANG, billId) != null) {
            log.info("[이용권] 서당 청구 중복 발급 요청 무시 — billId={}", billId);
            return;
        }
        grant(studentId, centerCode, productId, serviceCode, SOURCE_SEODANG, billId, totalCount);
    }
}
