package com.hohoedu.book_clinic.pass;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
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
 * 없다. 그래서 이용권을 결제에서 떼어냈고, 그 덕에 차감 코드에는 결제 방식 분기가 없다.
 *
 * [차감 시점] 2026-09-14부터 "예약할 때 깎고, 취소하면 되돌린다"다(이전에는 입실 시 차감).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PassService {

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    /**
     * 이용권 유효기간(일) — 유효기간 시작일부터 이만큼 쓸 수 있다(2026-09-14 정책).
     * 2026-10-27부터 "90일 당일까지"라 마지막 날은 시작일 + 90일이다(10/15 시작 → 1/13까지).
     */
    public static final int VALID_DAYS = 90;

    /**
     * 서비스 제공기간(일) — 결제일 + 이만큼 당일까지 이용권으로 예약할 수 있다(2026-10-27).
     * 이 날이 지나면 남은 이용권은 소멸하고 같은 수만큼 쿠폰으로 바뀐다({@link #convertToCoupon}).
     * 환불 기간도 이 기간과 같다.
     */
    public static final int SERVICE_DAYS = 90;

    /**
     * 유효기간 자동 시작(일) — 결제 후 이만큼 지나도록 첫 예약이 없으면 결제일 + 이 일수부터
     * 유효기간이 시작된다(2026-10-27). 첫 예약 회차가 이보다 늦어도 시작일은 여기로 당겨진다.
     * 그래서 서비스 종료 ~ 유효기간 종료(= 쿠폰 기간)는 누구든 최대 이 일수다.
     */
    public static final int AUTO_START_DAYS = 30;

    public static final String SOURCE_PG = "PG";
    public static final String SOURCE_SEODANG = "SEODANG";
    public static final String SOURCE_FREE = "FREE";
    public static final String SOURCE_COUPON = "COUPON";

    private final PassRepository passRepository;
    private final ApplicationEventPublisher eventPublisher;

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
                      String source, String refNo, String billingYm,
                      LocalDate validFrom, LocalDate validUntil, LocalDate serviceUntil, int totalCount) {
        passRepository.insertPass(studentId, centerCode, productId, serviceCode, source, refNo,
                billingYm, validFrom, validUntil, serviceUntil, totalCount);
    }

    /**
     * 유효기간 미배정 발급 (2026-09-14) — 책방 앱 결제(12회권)가 타는 경로다.
     *
     * 만료일이 "첫 예약이 잡힌 날부터 90일"이 되면서, 결제 시점에는 기간을 정할 수 없게 됐다.
     * 두 묶음을 한꺼번에 사두고 한 묶음을 다 쓴 뒤에 두 번째를 쓰기 시작하는 흐름이 정상이라,
     * 사놓고 아직 예약하지 않은 이용권은 **만료일 없이** 대기한다(valid_from/valid_until NULL).
     * 실제 기간은 {@link #consumeForReservation}이 그 이용권을 처음 깎을 때 확정한다.
     *
     * 서당 일괄청구분은 여전히 달력 월이라 {@link #grantMonthly}을 그대로 쓴다 — 두 체계가
     * 공존하므로 기간 판정 쿼리는 "NULL이면 아직 미배정"만 한 갈래 더 보면 된다.
     *
     * 서비스 제공기간(2026-10-27)은 결제 시점에 바로 정해진다 — 오늘(결제일) + 90일 당일까지.
     * 승인 확정과 같은 트랜잭션에서 불리므로 오늘이 곧 결제일이다(배치 복구는 몇 분 늦을 뿐이다).
     *
     * 발급 사실을 이벤트로 알린다(2026-10-27) — 결제대기 예약 확정과 멈춘 자동 예약 재개가 받는다.
     * 받는 쪽은 커밋 뒤에 돌므로, 결제가 롤백되면 아무 일도 일어나지 않는다.
     */
    public void grantUnassigned(String studentId, String centerCode, int productId, String serviceCode,
                                String source, String refNo, String billingYm, int totalCount) {
        grant(studentId, centerCode, productId, serviceCode, source, refNo, billingYm,
                null, null, KstClock.today().plusDays(SERVICE_DAYS), totalCount);
        eventPublisher.publishEvent(new PassGrantedEvent(studentId));
    }

    /**
     * 달력 월 단위 발급 — 서당 일괄청구분 전용이다.
     *
     * 2026-09-07 자동결제 전환으로 앱 결제분의 주기는 "결제일 기준 1개월"이 됐지만, 서당은
     * 여전히 전월 20일에 다음 달치를 걷는 월 단위 청구다. 그쪽 주기를 억지로 바꾸면 all_pass의
     * 청구 내역과 대조가 깨지므로 두 체계를 그대로 공존시킨다 — 발급된 이용권은 어느 쪽이든
     * valid_from~valid_until 한 쌍으로 표현되므로, 차감·상한·환불 쿼리는 한 벌로 유지된다.
     */
    public void grantMonthly(String studentId, String centerCode, int productId, String serviceCode,
                             String source, String refNo, String billingYm, int totalCount) {
        YearMonth ym = YearMonth.parse(billingYm, YM);
        grant(studentId, centerCode, productId, serviceCode, source, refNo, billingYm,
                ym.atDay(1), ym.atEndOfMonth(), null, totalCount);
    }

    /**
     * 결제일 기준 1개월 주기의 마지막 날 — 3/15 결제면 4/14다(다음 청구일의 전날).
     *
     * 말일 처리는 LocalDate.plusMonths가 알아서 당겨준다(1/31 → 2/28, 그 전날 2/27).
     * 앵커일 자체는 구독이 따로 보존하므로 3월에는 다시 31일로 돌아온다.
     */
    public static LocalDate cycleEnd(LocalDate cycleFrom) {
        return cycleFrom.plusMonths(1).minusDays(1);
    }

    /**
     * 예약 차감 (2026-09-14 정책 변경 — 차감 시점이 입실에서 예약으로 옮겨졌다).
     *
     * [왜 예약 시점인가] 노쇼가 잔여를 그대로 돌려받는 구조였다. 예약만 잡아두고 오지 않으면
     * 정원은 묶였는데 횟수는 안 줄어서, 그 자리는 다른 학생도 쓸 수 없고 본인도 손실이 없다.
     * 예약이 곧 차감이 되면 자리를 잡는 행위 자체에 비용이 붙고, 대신 제때(회차 24시간 전)
     * 취소하면 {@link #restoreForReservation}으로 온전히 돌려받는다.
     *
     * 예약한 회차 날짜(serviceDate)를 덮는 이용권에서 깎는다 — 오늘 기준이 아니다. 9월 30일
     * 회차를 9월 14일에 예약했다면 9월 30일에 유효한 이용권이어야 그날 실제로 쓸 수 있다.
     *
     * 호출부(ReservationService.reserveOne)의 트랜잭션 안에서 실행되므로 별도 트랜잭션을 열지
     * 않는다 — false를 돌려주면 호출부가 예외를 던져 예약 자체(슬롯 정원 증가 포함)가 롤백된다.
     *
     * @return 차감 성공 여부. 그 날짜에 쓸 수 있는 이용권이 없으면 false
     */
    public boolean consumeForReservation(String studentId, String serviceCode,
                                         Long reservationId, LocalDate serviceDate) {
        PassRespDTO.PassDTO pass = passRepository.findUsablePassOn(studentId, serviceCode, serviceDate);
        if (pass == null) {
            log.info("[이용권] 예약 차감 실패(잔여 없음) — studentId={}, service={}, serviceDate={}",
                    studentId, serviceCode, serviceDate);
            return false;
        }

        // 아직 기간이 안 정해진 이용권이면 이 예약이 그 이용권의 "첫 예약"이다 — 예약한 회차
        // 날짜부터 90일로 기간을 확정한다(2026-09-14). 기준이 예약을 잡은 날이 아니라 회차
        // 날짜인 것은 정책 그대로다(9/14에 9/15 회차를 예약 → 9/15부터 90일).
        // 다만 회차가 결제일 + 30일보다 늦으면 시작일은 결제일 + 30일로 당긴다(2026-10-27).
        // 조건부 UPDATE라 0행이면 다른 요청이 먼저 활성화한 것이고, 그 경우엔 기간이 이 예약
        // 날짜를 덮는지 다시 봐야 하므로 선택부터 새로 한다.
        if (pass.getValidFrom() == null) {
            LocalDate start = activationStart(pass, serviceDate);
            if (passRepository.activatePass(pass.getPassId(), start, VALID_DAYS) == 0) {
                log.warn("[이용권] 활성화 경합 — passId={}, studentId={}", pass.getPassId(), studentId);
                pass = passRepository.findUsablePassOn(studentId, serviceCode, serviceDate);
                if (pass == null) {
                    return false;
                }
                if (pass.getValidFrom() == null) {
                    start = activationStart(pass, serviceDate);
                    if (passRepository.activatePass(pass.getPassId(), start, VALID_DAYS) == 0) {
                        return false;
                    }
                }
            }
            // 시작일을 당겼으면 회차 날짜가 기간 밖일 수 있다. 예약은 한 달 앞까지만 열리고 서비스
            // 기간(결제일 + 90일) 안에서만 잡을 수 있어 정상적으로는 생기지 않지만, 생기면 이 예약은
            // 이 이용권으로 쓸 수 없다 — false면 호출부가 예약째 롤백하므로 활성화도 함께 되돌아간다.
            if (pass.getValidFrom() == null && serviceDate.isAfter(start.plusDays(VALID_DAYS))) {
                log.warn("[이용권] 회차가 유효기간 밖 — passId={}, 시작={}, 회차={}", pass.getPassId(), start, serviceDate);
                return false;
            }
            log.info("[이용권] 유효기간 확정 — passId={}, {}부터 {}일", pass.getPassId(), start, VALID_DAYS);
        }

        // 조회와 갱신 사이에 다른 요청이 먼저 깎았으면 0행이 된다. 예약은 학생 행 락으로 직렬화돼
        // 있어 보통 일어나지 않지만, 발생하면 마지막 한 장을 놓친 것이라 실패로 처리한다.
        if (passRepository.decrementRemain(pass.getPassId()) == 0) {
            log.warn("[이용권] 예약 차감 경합 — passId={}, studentId={}", pass.getPassId(), studentId);
            return false;
        }
        passRepository.insertUse(pass.getPassId(), studentId, reservationId, serviceDate);
        return true;
    }

    /**
     * 예약 취소에 따른 차감 복구 (2026-09-14). 그 예약으로 깐 살아있는 차감 이력을 찾아
     * 잔여를 되돌리고 이력에 취소 시각을 찍는다.
     *
     * 되돌릴 게 없으면(입실 차감 시절의 옛 예약, 이미 복구된 건) 아무 일도 하지 않는다 — 취소
     * 자체를 막을 이유는 아니라서 예외로 올리지 않는다. 환불로 회수된 이용권은 incrementRemain의
     * 조건에서 걸러지므로, 환불받은 횟수가 취소로 되살아나지는 않는다.
     */
    public void restoreForReservation(Long reservationId) {
        if (reservationId == null) {
            return;
        }
        for (PassRespDTO.UseDTO use : passRepository.findLiveUsesByReservation(reservationId)) {
            // 이력을 먼저 무효화한다 — 0행이면 다른 요청이 이미 되돌린 것이므로 잔여는 건드리지
            // 않는다(순서를 바꾸면 같은 차감에 잔여가 두 번 더해질 수 있다).
            if (passRepository.markUseCanceled(use.getUseId()) == 0) {
                continue;
            }
            if (passRepository.incrementRemain(use.getPassId()) == 0) {
                restoreToCoupon(use.getPassId(), reservationId);
                continue;
            }
            // 첫 예약을 취소했다면 그 이용권은 아직 한 번도 쓰지 않은 상태로 되돌아간다 —
            // 확정해둔 90일 기간도 함께 풀어 미배정으로 되돌린다(2026-09-14). 그러지 않으면
            // 예약했다 바로 취소한 것만으로 만료일이 박혀버린다. 다른 예약이 아직 그 이용권을
            // 쓰고 있으면(살아있는 차감이 남아 있으면) 기간은 그대로 유지한다.
            //
            // 단, 결제 후 30일이 이미 지났다면 미배정이 아니라 "결제일 + 30일 시작"으로 되돌린다
            // (2026-10-27) — 예약이 없었다면 자동 시작됐을 기간이 정답이다.
            if (passRepository.countUse(use.getPassId()) == 0) {
                passRepository.deactivatePass(use.getPassId());
                PassRespDTO.PassDTO pass = passRepository.lockPass(use.getPassId());
                LocalDate autoStart = autoStartDate(pass);
                if (autoStart != null && !KstClock.today().isBefore(autoStart)) {
                    passRepository.activatePass(use.getPassId(), autoStart, VALID_DAYS);
                    log.info("[이용권] 첫 예약 취소 — 자동 시작일로 기간 재설정. passId={}, {}부터", use.getPassId(), autoStart);
                } else {
                    log.info("[이용권] 유효기간 해제(첫 예약 취소) — passId={}", use.getPassId());
                }
            }
        }
    }

    /**
     * 유효기간 시작일 = min(첫 예약 회차 날짜, 결제일 + 30일) (2026-10-27).
     * 서비스 제공기간이 없는 이용권(서당/무상)이나 지급일이 없는 경우는 회차 날짜 그대로다.
     */
    static LocalDate activationStart(PassRespDTO.PassDTO pass, LocalDate serviceDate) {
        LocalDate autoStart = autoStartDate(pass);
        return autoStart != null && serviceDate.isAfter(autoStart) ? autoStart : serviceDate;
    }

    /** 결제일 + 30일 — 서비스 제공기간이 있는 앱 결제분만 해당한다(없으면 null) */
    private static LocalDate autoStartDate(PassRespDTO.PassDTO pass) {
        if (pass == null || pass.getServiceUntil() == null || pass.getGrantedAt() == null) {
            return null;
        }
        return pass.getGrantedAt().toLocalDate().plusDays(AUTO_START_DAYS);
    }

    /**
     * 소멸한 이용권의 예약을 취소했을 때 — 그 1회를 쿠폰으로 돌려준다(2026-10-27).
     *
     * incrementRemain이 0행인 경우는 둘이다: 환불로 회수된 이용권(돌려주지 않는다)과 쿠폰으로
     * 전환된 이용권(쿠폰으로 돌려준다). 행 잠금으로 읽어서 전환 배치와 겹치지 않게 한다.
     */
    private void restoreToCoupon(int passId, Long reservationId) {
        PassRespDTO.PassDTO pass = passRepository.lockPass(passId);
        if (pass == null || pass.getRevokedAt() != null || pass.getConvertedAt() == null) {
            log.info("[이용권] 복구 대상 아님(회수된 이용권) — passId={}, reservationId={}", passId, reservationId);
            return;
        }
        if (!issueCoupon(pass, 1)) {
            log.info("[이용권] 쿠폰 기간이 없어 복구 안 함 — passId={}, reservationId={}", passId, reservationId);
            return;
        }
        log.info("[이용권] 소멸 후 예약 취소 → 쿠폰 1장 복구 — passId={}, reservationId={}", passId, reservationId);
    }

    /**
     * 쿠폰 발급 — 이용권 1장당 쿠폰 행 1개. 이미 있으면 횟수만 더한다.
     * 쿠폰 기간 = 서비스 제공기간 다음 날 ~ 원래 이용권의 유효기간 마지막 날.
     *
     * @return 발급했으면 true. 유효기간이 서비스 기간보다 늦게 끝나지 않아 쿠폰 기간이 없으면 false
     */
    private boolean issueCoupon(PassRespDTO.PassDTO pass, int count) {
        LocalDate couponFrom = pass.getServiceUntil().plusDays(1);
        if (pass.getValidUntil() == null || pass.getValidUntil().isBefore(couponFrom)) {
            return false;
        }
        String refNo = String.valueOf(pass.getPassId());
        PassRespDTO.PassDTO coupon = passRepository.findCouponOf(refNo);
        if (coupon != null) {
            passRepository.addToCoupon(coupon.getPassId(), count);
        } else {
            grant(pass.getStudentId(), pass.getCenterCode(), pass.getProductId(), pass.getServiceCode(),
                    SOURCE_COUPON, refNo, pass.getBillingYm(), couponFrom, pass.getValidUntil(), null, count);
        }
        return true;
    }

    /**
     * 서비스 제공기간이 끝난 이용권 1장을 소멸시키고 남은 횟수만큼 쿠폰으로 바꾼다(2026-10-27).
     * {@code PassExpiryJob}이 한 장씩 부른다 — 한 장이 실패해도 나머지는 진행돼야 해서 트랜잭션을 장 단위로 끊는다.
     *
     * @return 발급한 쿠폰 수 (남은 횟수가 없거나 쿠폰 기간이 없으면 0)
     */
    @Transactional
    public int convertToCoupon(int passId) {
        PassRespDTO.PassDTO pass = passRepository.lockPass(passId);
        if (pass == null || pass.getConvertedAt() != null || pass.getRevokedAt() != null
                || pass.getServiceUntil() == null) {
            return 0;
        }
        int remain = pass.getRemainCount();
        passRepository.markConverted(passId);
        if (remain <= 0) {
            return 0;
        }
        if (!issueCoupon(pass, remain)) {
            log.info("[이용권] 서비스 기간 종료 — 쿠폰 기간 없음, {}회 소멸. passId={}, 유효기간 끝={}",
                    remain, passId, pass.getValidUntil());
            return 0;
        }
        log.info("[이용권] 서비스 기간 종료 → 쿠폰 {}장 ({} ~ {}). passId={}",
                remain, pass.getServiceUntil().plusDays(1), pass.getValidUntil(), passId);
        return remain;
    }

    /** 결제 후 30일 동안 첫 예약이 없는 이용권의 유효기간을 시작시킨다 — {@code PassExpiryJob}이 부른다 */
    @Transactional
    public int autoStartUnassigned(LocalDate today) {
        return passRepository.autoStartUnassigned(today, AUTO_START_DAYS, VALID_DAYS);
    }

    /** 서비스 제공기간이 끝났는데 아직 전환되지 않은 이용권 — {@code PassExpiryJob}이 부른다 */
    public List<PassRespDTO.PassDTO> findServiceEnded(LocalDate today) {
        return passRepository.findServiceEnded(today);
    }

    /** 이 학생이 이 서비스에 쓸 수 있는 총 잔여 횟수 */
    public int remain(String studentId, String serviceCode) {
        return passRepository.sumRemain(studentId, serviceCode);
    }

    /**
     * {@code date}가 속한 이용 주기 — 그 기간, 그 기간의 총 횟수(capacity), 남은 횟수(remaining).
     *
     * 자동결제 전환(2026-09-07) 전에는 "그 달"이 곧 단위였지만, 주기가 결제일 기준 1개월이 되면서
     * 한 달에 두 주기가 걸치게 됐다. 그래서 달력 월이 아니라 이 주기를 단위로 삼는다.
     *
     * 2026-09-14 예약 시 차감 전환 이후, 예약 가능 횟수는 remaining이다 — 예약 자체가 잔여를
     * 깎으므로 별도로 예약 건수를 세지 않는다. capacity는 화면에 총량을 보여주는 용도로만 쓴다.
     *
     * 그 날짜를 덮는 이용권이 없으면 둘 다 0에 기간은 null이다 — 결제 전까지 예약을 막는 근거다.
     */
    public PassRespDTO.CycleDTO cycleOn(String studentId, String serviceCode, LocalDate date) {
        PassRespDTO.CycleDTO cycle = passRepository.findCycleOn(studentId, serviceCode, date);
        if (cycle == null) {
            cycle = new PassRespDTO.CycleDTO();
        }
        return cycle;
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
     * 걷는 자체 주기가 있어, 우리가 임의로 추측하면 안 된다.
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
        grantMonthly(studentId, centerCode, productId, serviceCode, SOURCE_SEODANG, billId, billingYm, totalCount);
    }
}
