package com.hohoedu.book_clinic.schedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.handler.exception.Exception404;
import com.hohoedu.book_clinic._core.utils.KstClock;
import com.hohoedu.book_clinic.schedule._dto.MaterializeDTO;
import com.hohoedu.book_clinic.schedule._dto.ScheduleReqDTO;
import com.hohoedu.book_clinic.schedule._dto.ScheduleRespDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 예외 일정 — 특정 기간의 휴무·운영시간 변경·회차 변경.
 *
 * [요일 규칙과의 관계] 예외는 규칙을 고치지 않는다. "이 기간만 다르게"를 따로 적어두고,
 * 두 입력을 합치는 일은 ScheduleMaterializer가 한다. 그래서 예외를 지우면 별도 복구 없이
 * 요일 규칙대로 슬롯이 되살아난다 — 등록·삭제 양쪽에서 materialize를 다시 돌리는 이유다.
 *
 * [수정 API가 없다] 결정 2. 고칠 일이 생기면 삭제 후 재등록한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleExceptionService {

    private static final String TYPE_CLOSED = "CLOSED";
    private static final String TYPE_TIME_CHANGE = "TIME_CHANGE";
    private static final String TYPE_SLOT_CHANGE = "SLOT_CHANGE";

    /** 끝난 예외도 이 기간만큼은 목록에 남긴다 — "지난주 휴무가 왜 걸렸더라"를 확인할 데가 여기뿐 */
    private static final int LIST_PAST_DAYS = 7;
    private static final int REASON_MAX = 200;
    private static final int CAPACITY_MAX = 200;

    private final ScheduleExceptionRepository scheduleExceptionRepository;
    private final SlotInstanceRepository slotInstanceRepository;
    private final ScheduleMaterializer scheduleMaterializer;

    // ── 조회 ─────────────────────────────────────────────────────────────

    public List<ScheduleRespDTO.ExceptionDTO> findExceptions(String centerCode) {
        List<ScheduleRespDTO.ExceptionDTO> exceptions = scheduleExceptionRepository
                .findExceptions(centerCode, KstClock.today().minusDays(LIST_PAST_DAYS));
        if (exceptions.isEmpty()) {
            return exceptions;
        }

        List<Integer> ids = exceptions.stream().map(ScheduleRespDTO.ExceptionDTO::getExceptionId).toList();
        Map<Integer, List<MaterializeDTO.ExceptionSlotDTO>> slotsById = scheduleExceptionRepository
                .findExceptionSlots(ids).stream()
                .collect(Collectors.groupingBy(MaterializeDTO.ExceptionSlotDTO::getExceptionId));

        LocalDate today = KstClock.today();
        for (ScheduleRespDTO.ExceptionDTO ex : exceptions) {
            ex.setExceptionTypeLabel(typeLabel(ex.getExceptionType()));
            // 회차 정보는 종류를 가리지 않고 내려준다 — TIME_CHANGE는 그날 회차를 그대로 담고 있어서
            // 화면이 "그날 몇 시에 몇 회차인지"를 그리려면 이 값이 필요하다.
            ex.setSlotOverrides(slotsById.getOrDefault(ex.getExceptionId(), List.of()));
            if (TYPE_SLOT_CHANGE.equals(ex.getExceptionType())) {
                ex.setSlotSummary(slotSummary(ex.getSlotOverrides()));
            }
            applyStatus(ex, today);
        }
        return exceptions;
    }

    /** "3, 4회차 마감 / 2회차 정원 5명"처럼 목록에 한 줄로 보여줄 요약을 만든다 */
    private String slotSummary(List<MaterializeDTO.ExceptionSlotDTO> slots) {
        if (slots == null || slots.isEmpty()) {
            return null;
        }
        List<String> closed = new ArrayList<>();
        List<String> capacities = new ArrayList<>();
        for (MaterializeDTO.ExceptionSlotDTO slot : slots) {
            if (Boolean.TRUE.equals(slot.getIsClosed())) {
                closed.add(String.valueOf(slot.getSeq()));
            } else if (slot.getCapacity() != null) {
                // 마감된 회차의 정원은 의미가 없으므로 마감이 아닐 때만 정원을 말한다
                capacities.add(slot.getSeq() + "회차 정원 " + slot.getCapacity() + "명");
            }
        }

        List<String> parts = new ArrayList<>();
        if (!closed.isEmpty()) {
            parts.add(String.join(", ", closed) + "회차 마감");
        }
        parts.addAll(capacities);
        return parts.isEmpty() ? null : String.join(" / ", parts);
    }

    /** 스케줄 버전 카드와 같은 3단계 상태 — 한 타임라인에 섞이므로 판정 기준을 맞춘다 */
    private void applyStatus(ScheduleRespDTO.ExceptionDTO ex, LocalDate today) {
        String status = ex.getStartDate().isAfter(today) ? "UPCOMING"
                : ex.getEndDate().isBefore(today) ? "PAST" : "CURRENT";
        ex.setStatus(status);
        ex.setStatusLabel(switch (status) {
            case "UPCOMING" -> "예정";
            case "PAST" -> "지난";
            default -> "진행중";
        });
    }

    private String typeLabel(String type) {
        return switch (type == null ? "" : type) {
            case TYPE_CLOSED -> "휴무";
            case TYPE_TIME_CHANGE -> "운영시간 변경";
            case TYPE_SLOT_CHANGE -> "운영회차 변경";
            default -> type;
        };
    }

    // ── 등록 ─────────────────────────────────────────────────────────────

    @Transactional
    public Integer create(String centerCode, String userId, ScheduleReqDTO.SaveExceptionReqDTO req) {
        normalizeAndValidate(req);
        checkOverlap(centerCode, req);
        checkNoReservations(centerCode, req.getStartDate(), req.getEndDate(), "예외 일정을 등록할 수 없습니다");

        scheduleExceptionRepository.insertException(centerCode, req, userId);
        if (req.getSlots() != null && !req.getSlots().isEmpty()) {
            scheduleExceptionRepository.insertExceptionSlots(req.getExceptionId(), req.getSlots());
        }

        MaterializeDTO.ResultDTO result = materializeAffected(centerCode, req.getStartDate(), req.getEndDate());
        log.info("[운영스케줄] 예외 등록 — center={}, id={}, {}~{}, type={}, 슬롯 재생성 {}건, by={}",
                centerCode, req.getExceptionId(), req.getStartDate(), req.getEndDate(),
                req.getExceptionType(), result.getSlotCount(), userId);
        return req.getExceptionId();
    }

    /**
     * 값 보정 + 검증. 종류마다 필요한 필드가 완전히 달라서, 종류를 먼저 확정하고 그 종류에
     * 무의미한 필드는 비운다 — 안 비우면 "휴무인데 운영시간이 남아 있는" 예외가 저장된다.
     */
    private void normalizeAndValidate(ScheduleReqDTO.SaveExceptionReqDTO req) {
        LocalDate today = KstClock.today();
        String type = req.getExceptionType() == null ? "" : req.getExceptionType().trim().toUpperCase();
        if (!TYPE_CLOSED.equals(type) && !TYPE_TIME_CHANGE.equals(type) && !TYPE_SLOT_CHANGE.equals(type)) {
            throw new Exception400("예외 종류가 올바르지 않습니다.");
        }
        req.setExceptionType(type);

        if (req.getStartDate().isAfter(req.getEndDate())) {
            throw new Exception400("예외 종료일은 시작일과 같거나 늦어야 합니다.");
        }
        if (req.getStartDate().isBefore(today)) {
            throw new Exception400("지난 날짜에는 예외 일정을 등록할 수 없습니다.");
        }

        String reason = req.getReason().trim();
        if (reason.isEmpty()) {
            throw new Exception400("변경 사유를 입력해주세요.");
        }
        if (reason.length() > REASON_MAX) {
            throw new Exception400("변경 사유는 " + REASON_MAX + "자 이내로 입력해주세요.");
        }
        req.setReason(reason);

        if (TYPE_TIME_CHANGE.equals(type)) {
            LocalTime open = req.getOpenTime();
            LocalTime close = req.getCloseTime();
            if (open == null || close == null) {
                throw new Exception400("변경할 운영 시작·종료 시각을 입력해주세요.");
            }
            if (!open.isBefore(close)) {
                throw new Exception400("운영 종료 시각은 시작 시각보다 늦어야 합니다.");
            }
            validateTimeChangeSlots(req, open, close);
            return;
        }

        // TIME_CHANGE가 아니면 운영시간은 의미가 없다 (DB CHECK 제약과도 일치)
        req.setOpenTime(null);
        req.setCloseTime(null);

        if (TYPE_CLOSED.equals(type)) {
            req.setSlots(List.of());
            return;
        }

        // 이하 SLOT_CHANGE — 하루짜리만 허용된다(DB CK_schedule_exc_slot_single)
        if (!req.getStartDate().isEqual(req.getEndDate())) {
            throw new Exception400("회차 변경은 하루만 지정할 수 있습니다. 기간이 필요하면 날짜별로 등록해주세요.");
        }
        List<ScheduleReqDTO.ExceptionSlotReqDTO> slots = req.getSlots();
        if (slots == null || slots.isEmpty()) {
            throw new Exception400("변경할 회차를 1개 이상 선택해주세요.");
        }

        Set<Integer> seen = new HashSet<>();
        for (ScheduleReqDTO.ExceptionSlotReqDTO slot : slots) {
            if (slot.getSeq() == null || slot.getSeq() < 1) {
                throw new Exception400("회차 번호가 올바르지 않습니다.");
            }
            if (!seen.add(slot.getSeq())) {
                throw new Exception400(slot.getSeq() + "회차가 두 번 들어왔습니다.");
            }
            if (slot.getIsClosed() == null) {
                slot.setIsClosed(false);
            }
            // capacity는 null을 유지해야 한다 — "원래 정원 그대로"라는 뜻이라 0으로 바꾸면 정원 0명이 된다
            if (slot.getCapacity() != null && (slot.getCapacity() < 0 || slot.getCapacity() > CAPACITY_MAX)) {
                throw new Exception400(slot.getSeq() + "회차 인원은 0~" + CAPACITY_MAX + "명 사이여야 합니다.");
            }
            if (!Boolean.TRUE.equals(slot.getIsClosed()) && slot.getCapacity() == null) {
                throw new Exception400(slot.getSeq() + "회차에 바뀌는 내용이 없습니다. 마감하거나 정원을 지정해주세요.");
            }
        }
    }

    /**
     * 운영시간 변경 예외의 회차 검증.
     *
     * 회차를 함께 받는 이유는, 운영시간만 바꾸면 회차를 어떻게 할지 정할 방법이 없어서다.
     * 줄이면 남길 회차를 고르면 되지만 늘리거나 다른 시간대로 옮기면 만들 회차가 없다.
     * 그래서 그날 회차는 관리자가 확정하고, 서버는 그것이 말이 되는지만 본다.
     *
     * 요일 규칙 저장(saveWeek)과 같은 기준으로 검사한다 — 운영시간 안에 들어갈 것, 서로 겹치지
     * 않을 것, 인원이 범위 안일 것. 회차 번호는 화면 순서를 믿지 않고 시작시각 순으로 다시 매긴다.
     */
    private void validateTimeChangeSlots(ScheduleReqDTO.SaveExceptionReqDTO req, LocalTime open, LocalTime close) {
        List<ScheduleReqDTO.ExceptionSlotReqDTO> slots = req.getSlots();
        if (slots == null || slots.isEmpty()) {
            // 회차를 안 보내면 요일 템플릿에서 새 시간대에 들어가는 것만 남기는 예전 동작으로 돈다
            req.setSlots(List.of());
            return;
        }

        slots.sort(Comparator.comparing(ScheduleReqDTO.ExceptionSlotReqDTO::getStartTime,
                Comparator.nullsLast(Comparator.naturalOrder())));

        LocalTime prevEnd = null;
        for (int i = 0; i < slots.size(); i++) {
            ScheduleReqDTO.ExceptionSlotReqDTO slot = slots.get(i);
            String label = (i + 1) + "회차";

            if (slot.getStartTime() == null || slot.getEndTime() == null) {
                throw new Exception400(label + " 시작·종료 시각을 입력해주세요.");
            }
            if (!slot.getStartTime().isBefore(slot.getEndTime())) {
                throw new Exception400(label + "의 종료 시각은 시작 시각보다 늦어야 합니다.");
            }
            if (slot.getStartTime().isBefore(open) || slot.getEndTime().isAfter(close)) {
                throw new Exception400(label + "가 변경한 운영 시간을 벗어납니다.");
            }
            if (prevEnd != null && slot.getStartTime().isBefore(prevEnd)) {
                throw new Exception400(label + "의 시간이 앞 회차와 겹칩니다.");
            }
            if (slot.getCapacity() == null || slot.getCapacity() < 1 || slot.getCapacity() > CAPACITY_MAX) {
                throw new Exception400(label + " 인원은 1~" + CAPACITY_MAX + "명 사이여야 합니다.");
            }

            slot.setSeq(i + 1);
            slot.setIsClosed(Boolean.TRUE.equals(slot.getIsClosed()));
            prevEnd = slot.getEndTime();
        }
    }

    /** 겹침 규칙은 ddl-schedule.sql 상단 주석 그대로 — 기간끼리만 막고, 기간 안의 회차 변경은 허용 */
    private void checkOverlap(String centerCode, ScheduleReqDTO.SaveExceptionReqDTO req) {
        if (TYPE_SLOT_CHANGE.equals(req.getExceptionType())) {
            ScheduleRespDTO.ExceptionDTO same =
                    scheduleExceptionRepository.findSlotChangeOn(centerCode, req.getStartDate());
            if (same != null) {
                throw new Exception400(req.getStartDate() + "에 이미 회차 변경이 등록돼 있습니다. 삭제 후 다시 등록해주세요.");
            }
            return;
        }

        List<ScheduleRespDTO.ExceptionDTO> overlapped = scheduleExceptionRepository
                .findOverlappingPeriods(centerCode, req.getStartDate(), req.getEndDate());
        if (!overlapped.isEmpty()) {
            ScheduleRespDTO.ExceptionDTO first = overlapped.get(0);
            throw new Exception400(first.getStartDate() + "~" + first.getEndDate() + " 기간에 이미 '"
                    + typeLabel(first.getExceptionType()) + "' 예외가 등록돼 있어 기간이 겹칩니다.");
        }
    }

    // ── 삭제 ─────────────────────────────────────────────────────────────

    @Transactional
    public void delete(String centerCode, String userId, Integer exceptionId) {
        ScheduleRespDTO.ExceptionDTO target = scheduleExceptionRepository.findById(centerCode, exceptionId);
        if (target == null) {
            throw new Exception404("예외 일정을 찾을 수 없습니다.");
        }
        if (Boolean.TRUE.equals(target.getPast())) {
            throw new Exception400("이미 지난 예외 일정은 삭제할 수 없습니다.");
        }
        checkNoReservations(centerCode, target.getStartDate(), target.getEndDate(), "예외 일정을 삭제할 수 없습니다");

        scheduleExceptionRepository.archiveExceptionSlots(exceptionId, "DELETE", userId);
        scheduleExceptionRepository.archiveException(exceptionId, "DELETE", userId);
        scheduleExceptionRepository.deleteException(centerCode, exceptionId);

        // 삭제한 뒤에 다시 찍어야 요일 규칙대로 복구된다. 순서가 바뀌면 지운 예외가 그대로 반영된다.
        MaterializeDTO.ResultDTO result =
                materializeAffected(centerCode, target.getStartDate(), target.getEndDate());
        log.info("[운영스케줄] 예외 삭제 — center={}, id={}, {}~{}, 슬롯 재생성 {}건, by={}",
                centerCode, exceptionId, target.getStartDate(), target.getEndDate(), result.getSlotCount(), userId);
    }

    // ── 공통 ─────────────────────────────────────────────────────────────

    /**
     * 결정 4 — 예약이 걸린 기간은 등록·삭제 모두 막는다.
     *
     * 슬롯만 조용히 바꾸면 이미 예약한 학생의 시간이 말없이 사라지거나 옮겨진다. 엔진은 그런 날짜를
     * 건너뛰도록 돼 있지만, 그러면 "저장은 됐는데 일부 날짜만 반영 안 된" 어정쩡한 상태가 남으므로
     * 아예 들어오기 전에 막고 어느 날짜가 문제인지 알려준다.
     */
    private void checkNoReservations(String centerCode, LocalDate startDate, LocalDate endDate, String action) {
        LocalDate from = maxOf(startDate, KstClock.today());
        if (from.isAfter(endDate)) {
            return;
        }
        List<LocalDate> reserved = slotInstanceRepository.findReservedDatesInRange(centerCode, from, endDate);
        if (!reserved.isEmpty()) {
            throw new Exception400(reserved.get(0) + "에 이미 예약이 있어 " + action
                    + ". 예약을 확인한 뒤 다시 시도해주세요."
                    + (reserved.size() > 1 ? " (예약이 있는 날짜 " + reserved.size() + "일)" : ""));
        }
    }

    /** 지난 날짜는 손대지 않으므로 오늘부터 끝날까지만 다시 찍는다 */
    private MaterializeDTO.ResultDTO materializeAffected(String centerCode, LocalDate startDate, LocalDate endDate) {
        return scheduleMaterializer.materializeRange(centerCode, maxOf(startDate, KstClock.today()), endDate);
    }

    private LocalDate maxOf(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

}
