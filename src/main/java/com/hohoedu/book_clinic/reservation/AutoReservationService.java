package com.hohoedu.book_clinic.reservation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.handler.exception.Exception404;
import com.hohoedu.book_clinic._core.utils.KstClock;
import com.hohoedu.book_clinic.common.notification.NotificationService;
import com.hohoedu.book_clinic.common.notification._dto.NotificationReqDTO;
import com.hohoedu.book_clinic.pass.PassGrantedEvent;
import com.hohoedu.book_clinic.payment.SubscriptionService;
import com.hohoedu.book_clinic.payment._dto.SubscriptionRespDTO;
import com.hohoedu.book_clinic.reservation._dto.ReservationRespDTO;
import com.hohoedu.book_clinic.schedule.materialize.ScheduleMaterializer;
import com.hohoedu.book_clinic.student.StudentRepository;
import com.hohoedu.book_clinic.student.model.Student;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 매주 자동 예약 (2026-10-27).
 *
 * [흐름]
 *   ① 학생이 "매주 ○요일 ○회차"를 켜면 지금 열려 있는 날짜(오늘~30일)를 바로 예약한다.
 *   ② 새벽 배치(AutoReservationJob 03:25)가 스케줄이 새로 연 날짜를 이어서 예약한다.
 *   ③ 이용권이 없으면
 *        - 자동구매 학생: 그 자리에서 카드 결제 → 성공하면 예약, 실패하면 결제대기(PAY_WAIT)로 자리만 잡는다.
 *        - 수동결제 학생: 자리를 잡지 않고 멈춘다("결제를 할 거라는 신뢰가 없다").
 *   ④ 결제로 이용권이 생기면(PassGrantedEvent) 결제대기를 확정하고, 멈춘 날짜부터 다시 이어 예약한다.
 *   ⑤ 결과는 안내 대기열에 쌓았다가 오전 10시에 학생별로 묶어 한 번 푸시한다.
 *
 * [filled_until] 패턴마다 "여기까지는 시도했다"를 남긴다. 매일 열린 날짜 전체를 다시 훑으면
 * 학생이 일부러 취소한 날짜까지 다시 잡아버리기 때문이다. 대신 서버가 하루 꺼져 있어도 다음
 * 배치가 그 다음 날부터 이어가므로 구멍이 생기지 않는다. 이용권이 없어 멈출 때는 그 날짜를
 * 넘기지 않아서, 결제 뒤에 멈춘 날짜부터 다시 시작된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoReservationService {

    private static final String NOTICE_TITLE = "책방 예약 안내";

    /** 결제대기 자리를 이 일수 안으로 다가온 회차부터는 푼다 — 결제대기로는 입실할 수 없다 */
    private static final int PAY_WAIT_RELEASE_DAYS = 2;

    private final AutoReservationRepository autoRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;
    private final StudentRepository studentRepository;
    private final SubscriptionService subscriptionService;
    private final NotificationService notificationService;
    private final PlatformTransactionManager transactionManager;

    // ── 학생 앱 ──────────────────────────────────────────────────────────

    public List<ReservationRespDTO.AutoPatternDTO> findMine(String studentId) {
        return autoRepository.findByStudent(studentId);
    }

    /**
     * 자동 예약 켜기 — 패턴을 저장(이미 있으면 다시 켬)하고 시작일부터 열린 날짜를 바로 예약한다.
     * 학생이 화면을 보고 있으므로 결과는 응답으로 돌려주고 10시 안내에는 넣지 않는다.
     */
    public List<ReservationRespDTO.AutoAttemptDTO> register(String studentId, Integer dayOfWeek, Integer seq,
                                                            LocalDate startDate) {
        if (dayOfWeek == null || dayOfWeek < 1 || dayOfWeek > 7) {
            throw new Exception400("요일 값이 올바르지 않습니다.");
        }
        if (seq == null || seq < 1) {
            throw new Exception400("회차를 선택해주세요.");
        }
        if (studentRepository.findById(studentId) == null) {
            throw new Exception404("학생 정보를 찾을 수 없습니다.");
        }
        LocalDate today = KstClock.today();
        LocalDate start = (startDate == null || startDate.isBefore(today)) ? today : startDate;

        ReservationRespDTO.AutoPatternDTO pattern = autoRepository.findByKey(studentId, dayOfWeek, seq);
        if (pattern == null) {
            autoRepository.insertPattern(studentId, dayOfWeek, seq, start.minusDays(1));
        } else {
            autoRepository.enable(pattern.getAutoId(), start.minusDays(1));
        }
        pattern = autoRepository.findByKey(studentId, dayOfWeek, seq);
        log.info("[자동예약] 켜기 — studentId={}, 요일={}, 회차={}, 시작={}", studentId, dayOfWeek, seq, start);
        return fill(pattern, false, true);
    }

    /** 자동 예약 끄기 — 앞으로의 자동 예약만 멈춘다. 이미 잡힌 예약은 그대로 둔다 */
    public void turnOff(String studentId, Integer autoId) {
        ReservationRespDTO.AutoPatternDTO pattern = requireOwn(studentId, autoId);
        autoRepository.disable(pattern.getAutoId());
        log.info("[자동예약] 끄기 — studentId={}, autoId={}", studentId, autoId);
    }

    /** 다시 켜기 — 꺼져 있던 사이의 날짜는 일부러 비워둔 것으로 보고, 오늘부터 다시 연다 */
    public List<ReservationRespDTO.AutoAttemptDTO> turnOn(String studentId, Integer autoId) {
        ReservationRespDTO.AutoPatternDTO pattern = requireOwn(studentId, autoId);
        return register(studentId, pattern.getDayOfWeek(), pattern.getSeq(), null);
    }

    // ── 결제 직후 ────────────────────────────────────────────────────────

    /**
     * 이용권 발급 직후 — 결제대기를 확정하고 멈춰 있던 자동 예약을 이어 붙인다.
     *
     * 결제 트랜잭션이 커밋된 뒤에 돈다. 이 시점의 기존 트랜잭션은 이미 커밋돼 다시 커밋되지
     * 않으므로, 여기서 하는 저장은 전부 새 트랜잭션(REQUIRES_NEW)에서 해야 실제로 남는다.
     * 예외는 절대 밖으로 내보내지 않는다 — 결제는 이미 끝났고, 여기서 터지면 결제 응답이 실패로
     * 보인다. 놓친 예약은 다음 새벽 배치가 이어서 잡는다.
     *
     * 이 경로에서는 청구하지 않는다(allowCharge=false) — 방금 이용권이 생긴 학생이라 필요도 없고,
     * 결제 확정 콜백 안에서 또 결제를 부르면 흐름이 꼬인다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPassGranted(PassGrantedEvent event) {
        String studentId = event.studentId();
        try {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx.executeWithoutResult(status -> {
                int confirmed = reservationService.confirmPayWait(studentId);
                if (confirmed > 0) {
                    notice(studentId, "결제가 완료되어 결제대기 중이던 " + confirmed + "개 회차가 예약 확정되었습니다.");
                }
                for (ReservationRespDTO.AutoPatternDTO pattern : autoRepository.findEnabledByStudent(studentId)) {
                    autoRepository.clearNoPass(pattern.getAutoId());
                    fill(pattern, true, false);
                }
            });
        } catch (Exception e) {
            log.error("[자동예약] 결제 후 재개 실패 — studentId={} (다음 새벽 배치가 이어서 잡는다)", studentId, e);
        }
    }

    // ── 새벽 배치 ────────────────────────────────────────────────────────

    /**
     * 하루 한 번(03:25, 슬롯 생성 배치 뒤).
     *   ① 시작이 임박한 결제대기 정리
     *   ② 결제대기 학생 재결제(재시도일이 된 학생만 실제로 청구된다)
     *   ③ 켜져 있는 패턴 전부 이어서 예약
     */
    public void runDaily() {
        LocalDate today = KstClock.today();

        for (ReservationRespDTO.ReservationItemDTO r : reservationService.releasePayWaitStartingBefore(
                today.plusDays(PAY_WAIT_RELEASE_DAYS + 1L).atStartOfDay(), "결제 미완료로 결제대기 해제")) {
            notice(r.getStudentId(), reservationService.slotLabelOf(r.getServiceDate(), r.getSeq())
                    + " 결제대기가 결제되지 않아 취소되었습니다.");
        }

        for (String studentId : reservationRepository.findPayWaitStudentIds()) {
            try {
                retryPayWait(studentId);
            } catch (Exception e) {
                log.error("[자동예약] 결제대기 재결제 실패 — studentId={}", studentId, e);
            }
        }

        int patterns = 0;
        for (ReservationRespDTO.AutoPatternDTO pattern : autoRepository.findAllEnabled()) {
            try {
                // 앞선 처리(재결제 → 결제 후 재개)로 이미 이어진 패턴일 수 있어 최신 값을 다시 읽는다
                ReservationRespDTO.AutoPatternDTO fresh = autoRepository.findById(pattern.getAutoId());
                if (fresh != null && Boolean.TRUE.equals(fresh.getEnabled())) {
                    fill(fresh, true, true);
                    patterns++;
                }
            } catch (Exception e) {
                // 한 패턴이 실패해도 나머지 학생은 예약돼야 한다
                log.error("[자동예약] 패턴 처리 실패 — autoId={}", pattern.getAutoId(), e);
            }
        }
        log.info("[자동예약] 새벽 배치 완료 — 패턴 {}건", patterns);
    }

    /** 결제대기를 가진 학생의 재결제. 성공하면 결제 후 재개(onPassGranted)가 결제대기를 확정한다 */
    private void retryPayWait(String studentId) {
        SubscriptionRespDTO.ShortageChargeDTO result = subscriptionService.chargeForPassShortage(studentId);
        switch (result.getOutcome()) {
            case SubscriptionService.SHORTAGE_CHARGED -> notice(studentId, "이용권이 자동 구매되었습니다.");
            case SubscriptionService.SHORTAGE_FAILED -> noticeChargeFailed(studentId, result);
            case SubscriptionService.SHORTAGE_PAUSED, SubscriptionService.SHORTAGE_NONE ->
                    releaseAll(studentId, result.getOutcome());
            default -> { /* NOT_DUE — 재시도일 전. 자리는 그대로 유지 */ }
        }
    }

    // ── 10시 일괄 안내 ───────────────────────────────────────────────────

    /** 쌓인 안내를 학생별로 묶어 한 번씩 푸시한다. 토큰이 없거나 실패해도 다시 보내지 않는다(같은 안내 반복 방지) */
    public void sendNotices() {
        Map<String, List<ReservationRespDTO.AutoNoticeDTO>> byStudent = autoRepository.findUnsentNotices().stream()
                .collect(Collectors.groupingBy(ReservationRespDTO.AutoNoticeDTO::getStudentId,
                        LinkedHashMap::new, Collectors.toList()));
        int sent = 0;
        for (Map.Entry<String, List<ReservationRespDTO.AutoNoticeDTO>> entry : byStudent.entrySet()) {
            String studentId = entry.getKey();
            List<ReservationRespDTO.AutoNoticeDTO> lines = entry.getValue();
            String body = lines.stream().map(ReservationRespDTO.AutoNoticeDTO::getMessage)
                    .collect(Collectors.joining("\n"));
            try {
                notificationService.send(new NotificationReqDTO.SendReqDTO(NOTICE_TITLE, body, "STUDENT", studentId,
                        Map.of("type", "BOOKSTORE_RESERVATION", "studentId", studentId)), "SYSTEM");
                sent++;
            } catch (Exception e) {
                log.error("[자동예약] 안내 발송 실패 — studentId={}", studentId, e);
            }
            autoRepository.markNoticesSent(lines.stream().map(ReservationRespDTO.AutoNoticeDTO::getNoticeId).toList());
        }
        if (!byStudent.isEmpty()) {
            log.info("[자동예약] 10시 안내 — 대상 {}명, 발송 {}명", byStudent.size(), sent);
        }
    }

    // ── 내부 ─────────────────────────────────────────────────────────────

    /**
     * 패턴 하나를 filled_until 다음 날부터 예약 오픈일까지 이어서 예약한다.
     *
     * @param notify      결과를 10시 안내에 넣을지(학생이 직접 켠 경우는 화면이 결과를 보여주므로 false)
     * @param allowCharge 이용권이 없을 때 자동구매 청구를 시도할지(결제 직후 재개 경로는 false)
     */
    private List<ReservationRespDTO.AutoAttemptDTO> fill(ReservationRespDTO.AutoPatternDTO pattern,
                                                        boolean notify, boolean allowCharge) {
        List<ReservationRespDTO.AutoAttemptDTO> attempts = new ArrayList<>();
        String studentId = pattern.getStudentId();
        Student student = studentRepository.findById(studentId);
        if (student == null) {
            return attempts;
        }

        LocalDate today = KstClock.today();
        LocalDate horizon = today.plusDays(ScheduleMaterializer.RESERVATION_OPEN_DAYS);
        LocalDate from = pattern.getFilledUntil() == null ? today : pattern.getFilledUntil().plusDays(1);
        if (from.isBefore(today)) {
            from = today;
        }
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(horizon); d = d.plusDays(1)) {
            if (d.getDayOfWeek().getValue() == pattern.getDayOfWeek()) {
                dates.add(d);
            }
        }
        if (dates.isEmpty()) {
            return attempts;
        }

        Map<LocalDate, ReservationRespDTO.SlotOptionDTO> slots = reservationRepository
                .findSlotsByDates(student.getCenterCode(), dates, pattern.getSeq(), studentId).stream()
                .collect(Collectors.toMap(ReservationRespDTO.SlotOptionDTO::getServiceDate, Function.identity()));

        boolean subActive = subscriptionService.hasActiveSubscription(studentId);
        boolean chargeTried = false;

        for (LocalDate date : dates) {
            String label = reservationService.slotLabelOf(date, pattern.getSeq());
            ReservationRespDTO.SlotOptionDTO slot = slots.get(date);
            if (slot == null) {
                // 휴무 등으로 그날 그 회차가 없다 — 건너뛴다(슬롯 생성 배치가 먼저 돌았으므로 미생성이 아니다)
                autoRepository.updateFilledUntil(pattern.getAutoId(), date);
                continue;
            }
            if (Boolean.TRUE.equals(slot.getReservedByMe())) {
                attempts.add(attempt(date, pattern, ReservationService.AUTO_ALREADY, null, null));
                autoRepository.updateFilledUntil(pattern.getAutoId(), date);
                continue;
            }

            ReservationRespDTO.AutoAttemptDTO result =
                    reservationService.reserveAuto(studentId, slot.getSlotInstanceId(), !allowCharge && subActive);

            if (ReservationService.AUTO_NO_PASS.equals(result.getOutcome()) && allowCharge && subActive) {
                if (!chargeTried) {
                    chargeTried = true;
                    SubscriptionRespDTO.ShortageChargeDTO charge = subscriptionService.chargeForPassShortage(studentId);
                    switch (charge.getOutcome()) {
                        case SubscriptionService.SHORTAGE_CHARGED -> {
                            // 결제 후 재개(onPassGranted)가 이 패턴까지 이미 이어 예약했다 — 여기서 멈추고 결과만 다시 읽는다
                            if (notify) {
                                notice(studentId, "이용권이 자동 구매되었습니다.");
                            }
                            boolean booked = reservationRepository
                                    .findSlotsByDates(student.getCenterCode(), List.of(date), pattern.getSeq(), studentId)
                                    .stream().anyMatch(s -> Boolean.TRUE.equals(s.getReservedByMe()));
                            attempts.add(booked
                                    ? attempt(date, pattern, ReservationService.AUTO_RESERVED, null, null)
                                    : attempt(date, pattern, ReservationService.AUTO_FAILED, null,
                                            "결제는 완료됐지만 예약을 잡지 못했습니다. 다음 새벽에 다시 시도합니다."));
                            return attempts;
                        }
                        case SubscriptionService.SHORTAGE_PAUSED, SubscriptionService.SHORTAGE_NONE -> {
                            releaseAll(studentId, charge.getOutcome());
                            subActive = false;
                        }
                        case SubscriptionService.SHORTAGE_FAILED -> {
                            if (notify) {
                                noticeChargeFailed(studentId, charge);
                            }
                        }
                        default -> { /* NOT_DUE — 재시도일 전. 아래에서 결제대기로 잡는다 */ }
                    }
                }
                if (subActive) {
                    result = reservationService.reserveAuto(studentId, slot.getSlotInstanceId(), true);
                }
            }

            result.setServiceDate(date);
            result.setSeq(pattern.getSeq());
            attempts.add(result);

            switch (result.getOutcome()) {
                case ReservationService.AUTO_RESERVED -> {
                    autoRepository.clearNoPass(pattern.getAutoId());
                    autoRepository.updateFilledUntil(pattern.getAutoId(), date);
                    if (notify) {
                        notice(studentId, label + " 자동 예약되었습니다.");
                    }
                }
                case ReservationService.AUTO_HELD -> {
                    autoRepository.updateFilledUntil(pattern.getAutoId(), date);
                    if (notify) {
                        notice(studentId, label + " 결제대기로 자리를 잡아두었습니다.");
                    }
                }
                case ReservationService.AUTO_NO_PASS -> {
                    // 자리를 잡지 않고 멈춘다. filled_until을 넘기지 않아 결제 뒤 이 날짜부터 다시 시도된다
                    if (autoRepository.markNoPass(pattern.getAutoId(), today) > 0 && notify) {
                        notice(studentId, "이용권이 없어 " + label + "부터 자동 예약을 하지 못했습니다."
                                + " 이용권을 구매하시면 자동으로 이어집니다.");
                    }
                    return attempts;
                }
                default -> {
                    // 마감·하루 상한·24시간 이내 등 — 이 주만 건너뛴다
                    autoRepository.updateFilledUntil(pattern.getAutoId(), date);
                    if (notify && slot.getStartsAt() != null && slot.getStartsAt().isAfter(KstClock.now().plusDays(1))) {
                        notice(studentId, label + " 자동 예약 실패 — " + result.getMessage());
                    }
                }
            }
        }
        return attempts;
    }

    /** 자동구매가 중지·해지됐을 때 결제대기 자리를 모두 반납하고 알린다 */
    private void releaseAll(String studentId, String outcome) {
        String reason = SubscriptionService.SHORTAGE_PAUSED.equals(outcome)
                ? "자동구매 결제 실패로 결제대기 해제" : "자동구매 해지로 결제대기 해제";
        List<ReservationRespDTO.ReservationItemDTO> released = reservationService.releasePayWait(studentId, reason);
        if (SubscriptionService.SHORTAGE_PAUSED.equals(outcome)) {
            notice(studentId, "자동구매 카드 결제가 계속 실패해 자동구매가 중지되었습니다. 카드를 확인해주세요."
                    + (released.isEmpty() ? "" : " 결제대기로 잡아둔 " + released.size() + "개 회차는 취소되었습니다."));
        } else if (!released.isEmpty()) {
            notice(studentId, "자동구매가 해지되어 결제대기로 잡아둔 " + released.size() + "개 회차가 취소되었습니다.");
        }
    }

    private void noticeChargeFailed(String studentId, SubscriptionRespDTO.ShortageChargeDTO charge) {
        LocalDate retryOn = charge.getRetryOn();
        notice(studentId, "자동구매 카드 결제에 실패했습니다"
                + (charge.getReason() != null ? "(" + charge.getReason() + ")" : "") + "."
                + (retryOn != null
                        ? " " + retryOn.getMonthValue() + "월 " + retryOn.getDayOfMonth() + "일에 다시 시도하며, 그때까지 자리는 유지됩니다."
                        : ""));
    }

    private void notice(String studentId, String message) {
        try {
            autoRepository.insertNotice(studentId, message.length() > 200 ? message.substring(0, 200) : message);
        } catch (Exception e) {
            log.error("[자동예약] 안내 적재 실패 — studentId={}, message={}", studentId, message, e);
        }
    }

    private ReservationRespDTO.AutoAttemptDTO attempt(LocalDate date, ReservationRespDTO.AutoPatternDTO pattern,
                                                     String outcome, Long reservationId, String message) {
        ReservationRespDTO.AutoAttemptDTO dto = ReservationRespDTO.AutoAttemptDTO.of(outcome, reservationId, message);
        dto.setServiceDate(date);
        dto.setSeq(pattern.getSeq());
        return dto;
    }

    private ReservationRespDTO.AutoPatternDTO requireOwn(String studentId, Integer autoId) {
        if (autoId == null) {
            throw new Exception400("자동 예약을 선택해주세요.");
        }
        ReservationRespDTO.AutoPatternDTO pattern = autoRepository.findById(autoId);
        if (pattern == null || !studentId.equals(pattern.getStudentId())) {
            throw new Exception404("자동 예약 정보를 찾을 수 없습니다.");
        }
        return pattern;
    }
}
