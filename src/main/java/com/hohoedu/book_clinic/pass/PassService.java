package com.hohoedu.book_clinic.pass;

import java.time.LocalDate;
import java.time.YearMonth;
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
     *
     * billingYm(몇 월치인지)은 호출부가 반드시 정해서 넘긴다 — 여기서 "오늘 날짜 기준"으로
     * 자체 계산하지 않는다. PG 결제는 prepare() 시점에 정해서 화면에 보여준 달(payment.billing_ym)을
     * 그대로 받아써야 하고, 서당 청구는 all_pass가 정한 청구월을 받아써야 한다 — 각자 사정이 달라
     * 이 메서드가 "오늘이 몇 월이니 이번 달"이라고 임의로 정하면 둘 다 틀릴 수 있다.
     */
    public void grant(String studentId, String centerCode, int productId, String serviceCode,
                      String source, String refNo, String billingYm, int totalCount) {
        YearMonth ym = YearMonth.parse(billingYm, YM);
        passRepository.insertPass(studentId, centerCode, productId, serviceCode, source, refNo,
                billingYm, ym.atDay(1), ym.atEndOfMonth(), totalCount);
    }

    /**
     * 다음 결제의 대상월 — "이 학생이 이 서비스에서 가장 늦게까지 사둔 달"의 다음 달을 자동으로
     * 찾는다. 날짜 커트오프(예: 20일 이전/이후)를 쓰지 않는 이유는, 그런 고정 기준은 "이미 이번
     * 달을 샀는데 그 달의 20일 이전에 다음 달 걸 미리 사려는" 경우를 못 걸러내기 때문이다.
     * 대신 실제로 뭘 샀는지를 본다 — 산 적 없으면 이번 달, 이미 산 것 중 가장 늦은 달이 아직
     * 안 지났으면 그 다음 달, 지나버렸으면(공백기) 과거에 매이지 않고 이번 달로 리셋한다.
     */
    public String nextBillingYm(String studentId, String serviceCode) {
        LocalDate latestValidUntil = passRepository.findLatestValidUntil(studentId, serviceCode);
        YearMonth current = YearMonth.from(KstClock.today());
        if (latestValidUntil == null) {
            return current.format(YM);
        }
        YearMonth latestMonth = YearMonth.from(latestValidUntil);
        YearMonth target = latestMonth.isBefore(current) ? current : latestMonth.plusMonths(1);
        return target.format(YM);
    }

    /**
     * 출석 차감 — 2026-08-28 정책 변경으로 "입실일당 1회"가 아니라 "그날 예약한 회차(타임) 수만큼"
     * 깐다(하루 최대 4회차). {@code targetUnits}는 그날 출석 확정된 회차 수다.
     *
     * 같은 날 재입실은 이미 깐 만큼은 다시 깎지 않는다 — 로그아웃 후 재로그인·새로고침이 흔하기
     * 때문이다. "그날 목표 차감수 − 이미 차감한 수"만큼만 채우는 방식이라, 입실 후 같은 날 회차를
     * 더 예약하고 재입실하는 경우엔 늘어난 부족분만 추가로 깎는다.
     *
     * 이용권이 회차 수보다 모자라면 있는 만큼만 깎고 나머지는 포기한다(입실 자체는 허용). 다만
     * 오늘 한 번도 못 깠는데 이번에도 하나도 못 깎았다면(=쓸 이용권이 아예 없음) -1을 돌려
     * 호출부가 입실을 막게 한다.
     *
     * 행 수 = remain_count 감소량 불변식은 그대로다(1행 = 1차감) — 환불 로직이 pass_use 행 수에
     * 의존하므로 이 불변식을 깨면 안 된다.
     *
     * @return 차감 후 남은 횟수. 오늘 아무 것도 못 깎았고 쓸 이용권도 없으면 -1
     */
    @Transactional
    public int consume(String studentId, String serviceCode, Integer sessionId, int targetUnits) {
        LocalDate today = KstClock.today();
        int units = Math.max(targetUnits, 1);

        int alreadyCharged = passRepository.countTodayUse(studentId, today);
        int toCharge = units - alreadyCharged;
        if (toCharge <= 0) {
            return passRepository.sumRemain(studentId, serviceCode);
        }

        int charged = 0;
        for (int i = 0; i < toCharge; i++) {
            PassRespDTO.PassDTO pass = passRepository.findUsablePass(studentId, serviceCode);
            if (pass == null) {
                log.info("[이용권] 잔여 부족 — studentId={}, service={}, 목표 {}회 중 {}회만 차감",
                        studentId, serviceCode, toCharge, charged);
                break;
            }
            // 조회와 갱신 사이에 다른 요청이 먼저 깎았으면 0행이 된다 — 경합으로 보고 중단한다
            // (재입실 시 alreadyCharged가 늘어 있어 남은 부족분만 다시 시도된다).
            if (passRepository.decrementRemain(pass.getPassId()) == 0) {
                log.warn("[이용권] 차감 경합 — passId={}, studentId={}", pass.getPassId(), studentId);
                break;
            }
            passRepository.insertUse(pass.getPassId(), studentId, sessionId, today);
            charged++;
        }

        if (charged == 0 && alreadyCharged == 0) {
            log.info("[이용권] 잔여 없음 — studentId={}, service={}", studentId, serviceCode);
            return -1;
        }
        return passRepository.sumRemain(studentId, serviceCode);
    }

    /** 이 학생이 이 서비스에 쓸 수 있는 총 잔여 횟수 */
    public int remain(String studentId, String serviceCode) {
        return passRepository.sumRemain(studentId, serviceCode);
    }

    /**
     * {@code dateInMonth}가 속한 달에 이 학생이 살 수 있었던 이용권 총량(=그 달 예약 상한, 2026-08-28).
     * 그 달 이용권이 아직 없으면 0 — 그 달 예약을 막는 근거가 된다.
     */
    public int monthlyCapacity(String studentId, String serviceCode, LocalDate dateInMonth) {
        LocalDate monthStart = dateInMonth.withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
        return passRepository.sumMonthlyTotalCount(studentId, serviceCode, monthStart, monthEnd);
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
     * billingYm은 all_pass가 정한 청구월을 그대로 받는다 — 서당은 전월 20일에 다음 달치를
     * 걷는 자체 주기가 있어, 우리가 nextBillingYm()으로 임의 추측하면 안 된다.
     */
    @Transactional
    public void grantFromSeodang(String studentId, String centerCode, int productId, String serviceCode,
                                 String billId, String billingYm, int totalCount) {
        if (billId == null || billId.isBlank()) {
            throw new Exception400("서당 청구 식별자(bill_id)가 없습니다.");
        }
        if (passRepository.findByRef(SOURCE_SEODANG, billId) != null) {
            log.info("[이용권] 서당 청구 중복 발급 요청 무시 — billId={}", billId);
            return;
        }
        grant(studentId, centerCode, productId, serviceCode, SOURCE_SEODANG, billId, billingYm, totalCount);
    }
}
