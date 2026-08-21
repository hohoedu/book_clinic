package com.hohoedu.book_clinic.schedule.materialize;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hohoedu.book_clinic._core.utils.KstClock;
import com.hohoedu.book_clinic.schedule._dto.MaterializeDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 슬롯 실체화(materialize) 엔진 — 요일 규칙 + 기간 예외를 날짜에 적용해 slot_instance를 찍어낸다.
 *
 * [왜 규칙만으로는 안 되나] 예약이 붙는 대상은 "8월 20일 3회차"라는 구체적인 슬롯이지 "수요일
 * 3회차"라는 규칙이 아니다. 규칙만 두고 예약 때마다 계산하면, 규칙을 고치는 순간 이미 예약된
 * 학생의 시간이 소리 없이 바뀐다. 그래서 규칙은 입력값일 뿐이고 진실은 언제나 slot_instance다.
 *
 * [적용 우선순위] CLOSED 휴무 > TIME_CHANGE 운영시간 변경 > 요일 규칙, 그 위에 SLOT_CHANGE가
 * 회차 단위로 덧칠된다. SLOT_CHANGE는 앞의 세 가지와 배타적이지 않다 — 기간 휴무 안의 특정
 * 하루에 회차 조정을 얹는 게 허용된 조합이라(ddl-schedule.sql 겹침 검증 규칙) 따로 적용한다.
 *
 * [예약이 걸린 날짜] 손대지 않고 건너뛰고 blockedDates로 돌려준다. 예외를 던지지 않는 이유는
 * MaterializeDTO.ResultDTO 주석 참고.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleMaterializer {

    /**
     * 예약을 미리 열어두는 기간(일). 오늘부터 이만큼의 슬롯을 항상 실체화해둔다.
     * 값을 늘리면 더 먼 날짜까지 예약을 받을 수 있지만, 규칙을 바꿀 때 다시 찍어야 할 날짜도 늘어난다.
     */
    public static final int RESERVATION_OPEN_DAYS = 28;

    /** 템플릿에 정원이 비어 있는 회차에 쓸 최소 기본값 */
    private static final int CAPACITY_FALLBACK = 10;

    private static final String TYPE_CLOSED = "CLOSED";
    private static final String TYPE_TIME_CHANGE = "TIME_CHANGE";
    private static final String TYPE_SLOT_CHANGE = "SLOT_CHANGE";

    private final SlotInstanceRepository slotInstanceRepository;

    /** 오늘~예약 오픈일 전체를 다시 찍는다. 일일 배치·수동 재계산용 */
    @Transactional
    public MaterializeDTO.ResultDTO materializeHorizon(String centerCode) {
        LocalDate today = KstClock.today();
        return materializeRange(centerCode, today, today.plusDays(RESERVATION_OPEN_DAYS));
    }

    /** 기간 안의 모든 날짜를 다시 찍는다. 예외 등록·삭제 후 호출 */
    @Transactional
    public MaterializeDTO.ResultDTO materializeRange(String centerCode, LocalDate from, LocalDate to) {
        return run(centerCode, datesBetween(from, to));
    }

    /**
     * 기간 안에서 특정 요일에 해당하는 날짜만 다시 찍는다. 요일 규칙 저장 직후 호출 —
     * 수요일 규칙을 바꿨는데 4주치 전체 28일을 다시 계산할 이유가 없다.
     */
    @Transactional
    public MaterializeDTO.ResultDTO materializeDayOfWeek(String centerCode, int dayOfWeek, LocalDate from) {
        LocalDate to = KstClock.today().plusDays(RESERVATION_OPEN_DAYS);
        List<LocalDate> dates = datesBetween(from, to).stream()
                .filter(d -> d.getDayOfWeek().getValue() == dayOfWeek)
                .toList();
        return run(centerCode, dates);
    }

    private List<LocalDate> datesBetween(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            return List.of();
        }
        return from.datesUntil(to.plusDays(1)).toList();
    }

    // ── 엔진 본체 ────────────────────────────────────────────────────────

    private MaterializeDTO.ResultDTO run(String centerCode, List<LocalDate> dates) {
        LocalDate today = KstClock.today();

        // 지난 날짜는 손대지 않는다 — 그날 실제로 몇 시에 몇 명을 받았는지가 기록이기 때문.
        List<LocalDate> targets = dates.stream()
                .filter(d -> !d.isBefore(today))
                .distinct().sorted().toList();
        if (targets.isEmpty()) {
            return new MaterializeDTO.ResultDTO(0, 0, List.of());
        }

        LocalDate from = targets.get(0);
        LocalDate to = targets.get(targets.size() - 1);

        // 입력값은 날짜별로 쪼개 조회하지 않고 기간 전체를 한 번에 읽는다 (SlotInstanceMapper 주석 참고)
        Map<Integer, List<MaterializeDTO.RuleDTO>> rulesByDay = slotInstanceRepository
                .findRulesUpTo(centerCode, to).stream()
                .collect(Collectors.groupingBy(MaterializeDTO.RuleDTO::getDayOfWeek));

        Map<String, List<MaterializeDTO.TemplateSlotDTO>> slotsByVersion = new HashMap<>();
        for (MaterializeDTO.TemplateSlotDTO slot : slotInstanceRepository.findTemplateSlotsUpTo(centerCode, to)) {
            slotsByVersion.computeIfAbsent(versionKey(slot.getDayOfWeek(), slot.getEffectiveFrom()),
                    k -> new ArrayList<>()).add(slot);
        }

        List<MaterializeDTO.ExceptionDTO> exceptions =
                slotInstanceRepository.findExceptionsInRange(centerCode, from, to);
        Map<Integer, List<MaterializeDTO.ExceptionSlotDTO>> exceptionSlots = slotInstanceRepository
                .findExceptionSlotsInRange(centerCode, from, to).stream()
                .collect(Collectors.groupingBy(MaterializeDTO.ExceptionSlotDTO::getExceptionId));

        Set<LocalDate> reservedDates =
                new HashSet<>(slotInstanceRepository.findReservedDatesInRange(centerCode, from, to));

        int dateCount = 0;
        int slotCount = 0;
        List<LocalDate> blocked = new ArrayList<>();

        for (LocalDate date : targets) {
            if (reservedDates.contains(date)) {
                blocked.add(date);
                continue;
            }

            MaterializeDTO.RuleDTO rule = effectiveRule(rulesByDay.get(date.getDayOfWeek().getValue()), date);
            List<MaterializeDTO.InstanceDTO> desired = buildSlots(date, rule, slotsByVersion, exceptions, exceptionSlots);

            List<Integer> keepSeqs = desired.stream().map(MaterializeDTO.InstanceDTO::getSeq).toList();
            slotInstanceRepository.deleteObsoleteSlots(centerCode, date, keepSeqs);
            if (!desired.isEmpty()) {
                slotInstanceRepository.mergeSlots(centerCode, date, desired);
            }

            dateCount++;
            slotCount += desired.size();
        }

        if (!blocked.isEmpty()) {
            log.info("[운영스케줄] materialize 건너뜀 — center={}, 예약이 걸린 날짜={}", centerCode, blocked);
        }
        log.info("[운영스케줄] materialize — center={}, {}~{}, 날짜 {}건, 슬롯 {}건",
                centerCode, from, to, dateCount, slotCount);

        return new MaterializeDTO.ResultDTO(dateCount, slotCount, blocked);
    }

    /** 그 날짜에 유효한 버전 = effective_from이 날짜 이하인 것 중 가장 늦은 것 */
    private MaterializeDTO.RuleDTO effectiveRule(List<MaterializeDTO.RuleDTO> candidates, LocalDate date) {
        if (candidates == null) {
            return null;
        }
        MaterializeDTO.RuleDTO chosen = null;
        for (MaterializeDTO.RuleDTO rule : candidates) {   // 조회가 effective_from 오름차순이라 마지막 통과분이 최신
            if (!rule.getEffectiveFrom().isAfter(date)) {
                chosen = rule;
            }
        }
        return chosen;
    }

    /** 하루치 슬롯 확정 — 이 메서드가 우선순위 규칙 전부를 담고 있다 */
    private List<MaterializeDTO.InstanceDTO> buildSlots(
            LocalDate date,
            MaterializeDTO.RuleDTO rule,
            Map<String, List<MaterializeDTO.TemplateSlotDTO>> slotsByVersion,
            List<MaterializeDTO.ExceptionDTO> exceptions,
            Map<Integer, List<MaterializeDTO.ExceptionSlotDTO>> exceptionSlots) {

        MaterializeDTO.ExceptionDTO period = null;      // CLOSED / TIME_CHANGE (기간형, 겹치지 않게 검증됨)
        MaterializeDTO.ExceptionDTO slotChange = null;  // SLOT_CHANGE (하루짜리)
        for (MaterializeDTO.ExceptionDTO ex : exceptions) {
            if (date.isBefore(ex.getStartDate()) || date.isAfter(ex.getEndDate())) {
                continue;
            }
            if (TYPE_SLOT_CHANGE.equals(ex.getExceptionType())) {
                slotChange = ex;
            } else {
                period = ex;
            }
        }

        if (period != null && TYPE_CLOSED.equals(period.getExceptionType())) {
            return List.of();   // 휴무 — 회차 조정이 같이 걸려 있어도 열 슬롯 자체가 없다
        }

        if (rule == null || !Boolean.TRUE.equals(rule.getIsOpen())) {
            // 규칙이 없거나 휴무 요일이면 운영시간 예외가 걸려 있어도 열 수업 자체가 없다.
            return List.of();
        }

        List<MaterializeDTO.InstanceDTO> slots;
        if (period != null && TYPE_TIME_CHANGE.equals(period.getExceptionType())) {
            // 관리자가 그날 회차를 직접 확정했으면 그것이 답이다. 운영시간을 늘리거나 다른 시간대로
            // 옮기는 경우는 템플릿에서 남길 회차가 없어 확정값 없이는 만들 방법이 없다.
            List<MaterializeDTO.ExceptionSlotDTO> defined = definedSlots(exceptionSlots, period.getExceptionId());
            slots = defined.isEmpty()
                    ? keepWithinHours(fromTemplate(date, rule, slotsByVersion), date,
                            period.getOpenTime(), period.getCloseTime())
                    : fromExceptionSlots(date, defined, rule);
        } else {
            slots = fromTemplate(date, rule, slotsByVersion);
        }

        if (slotChange != null) {
            applyOverrides(slots, exceptionSlots.get(slotChange.getExceptionId()));
        }
        return slots;
    }

    /** 요일 규칙에 등록된 회차 템플릿을 그날 날짜에 붙여 슬롯으로 만든다 */
    private List<MaterializeDTO.InstanceDTO> fromTemplate(
            LocalDate date, MaterializeDTO.RuleDTO rule,
            Map<String, List<MaterializeDTO.TemplateSlotDTO>> slotsByVersion) {

        List<MaterializeDTO.TemplateSlotDTO> templates =
                slotsByVersion.get(versionKey(rule.getDayOfWeek(), rule.getEffectiveFrom()));
        if (templates == null) {
            return new ArrayList<>();
        }

        List<MaterializeDTO.InstanceDTO> slots = new ArrayList<>();
        for (MaterializeDTO.TemplateSlotDTO t : templates) {
            slots.add(instance(t.getSeq(),
                    LocalDateTime.of(date, t.getStartTime()),
                    LocalDateTime.of(date, t.getEndTime()),
                    t.getCapacity() != null ? t.getCapacity() : capacityOf(rule),
                    "TEMPLATE"));
        }
        return slots;
    }

    /** 시각이 채워진 예외 회차만 — 시각이 없으면 SLOT_CHANGE용 오버라이드라 그날 회차 정의가 아니다 */
    private List<MaterializeDTO.ExceptionSlotDTO> definedSlots(
            Map<Integer, List<MaterializeDTO.ExceptionSlotDTO>> exceptionSlots, Integer exceptionId) {

        return exceptionSlots.getOrDefault(exceptionId, List.of()).stream()
                .filter(slot -> slot.getStartTime() != null && slot.getEndTime() != null)
                .toList();
    }

    /** 관리자가 확정한 그날 회차를 그대로 슬롯으로 만든다 */
    private List<MaterializeDTO.InstanceDTO> fromExceptionSlots(
            LocalDate date, List<MaterializeDTO.ExceptionSlotDTO> defined, MaterializeDTO.RuleDTO rule) {

        List<MaterializeDTO.InstanceDTO> slots = new ArrayList<>();
        for (MaterializeDTO.ExceptionSlotDTO row : defined) {
            MaterializeDTO.InstanceDTO slot = instance(row.getSeq(),
                    LocalDateTime.of(date, row.getStartTime()),
                    LocalDateTime.of(date, row.getEndTime()),
                    row.getCapacity() != null ? row.getCapacity() : capacityOf(rule),
                    "EXCEPTION");
            if (Boolean.TRUE.equals(row.getIsClosed())) {
                slot.setStatus("CLOSED");
            }
            slots.add(slot);
        }
        return slots;
    }

    /**
     * 운영시간 변경 예외에서 회차를 따로 지정하지 않았을 때 —
     * 바뀐 시간대 안에 온전히 들어가는 회차만 남긴다.
     *
     * 회차를 새로 끊지 않는 것이 핵심이다. 13:00~19:00(1~7회차)을 15:00~19:00으로 줄이면
     * 남는 것은 원래 4~7회차이지 15:00부터 새로 매긴 1~4회차가 아니다. "오늘은 3시부터"는
     * 앞 타임이 없어진다는 뜻이지 시간표를 새로 짠다는 뜻이 아니고, 원래 15:30이던 수업이
     * 15:00으로 당겨지면 이미 그 시간을 알고 있는 학부모가 혼란스럽다.
     *
     * 회차 번호(seq)도 원래 것을 그대로 둔다 — 같은 날 SLOT_CHANGE가 "4회차 마감"을 지시할 때
     * 가리키는 번호가 흔들리면 안 된다.
     */
    private List<MaterializeDTO.InstanceDTO> keepWithinHours(
            List<MaterializeDTO.InstanceDTO> slots, LocalDate date, LocalTime open, LocalTime close) {

        if (open == null || close == null) {
            return slots;
        }
        LocalDateTime from = LocalDateTime.of(date, open);
        LocalDateTime to = LocalDateTime.of(date, close);

        List<MaterializeDTO.InstanceDTO> kept = new ArrayList<>();
        for (MaterializeDTO.InstanceDTO slot : slots) {
            // 걸쳐 있는 회차는 버린다 — 14:40~15:30을 15:00부터로 잘라 붙이면 수업 길이가 달라진다
            if (!slot.getStartsAt().isBefore(from) && !slot.getEndsAt().isAfter(to)) {
                slot.setSourceType("EXCEPTION");
                kept.add(slot);
            }
        }
        return kept;
    }

    /** SLOT_CHANGE의 회차별 덧칠 — 해당 회차만 마감하거나 정원을 바꾼다 */
    private void applyOverrides(List<MaterializeDTO.InstanceDTO> slots,
                                List<MaterializeDTO.ExceptionSlotDTO> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return;
        }
        Map<Integer, MaterializeDTO.ExceptionSlotDTO> bySeq = overrides.stream()
                .collect(Collectors.toMap(MaterializeDTO.ExceptionSlotDTO::getSeq, o -> o, (a, b) -> a));

        for (MaterializeDTO.InstanceDTO slot : slots) {
            MaterializeDTO.ExceptionSlotDTO override = bySeq.get(slot.getSeq());
            if (override == null) {
                continue;
            }
            if (Boolean.TRUE.equals(override.getIsClosed())) {
                // 행을 지우지 않고 CLOSED로 둔다 — 학생 화면에서 "마감"으로 보여야 하고,
                // 나중에 예외를 삭제하면 같은 slot_instance_id 그대로 되살아난다.
                slot.setStatus("CLOSED");
            }
            if (override.getCapacity() != null) {   // null은 "템플릿 인원 유지", 0은 "정원 0명"
                slot.setCapacity(override.getCapacity());
            }
            slot.setSourceType("EXCEPTION");
        }
    }

    private MaterializeDTO.InstanceDTO instance(int seq, LocalDateTime startsAt, LocalDateTime endsAt,
                                                int capacity, String sourceType) {
        MaterializeDTO.InstanceDTO slot = new MaterializeDTO.InstanceDTO();
        slot.setSeq(seq);
        slot.setStartsAt(startsAt);
        slot.setEndsAt(endsAt);
        slot.setCapacity(capacity);
        slot.setStatus("OPEN");
        slot.setSourceType(sourceType);
        return slot;
    }

    private int capacityOf(MaterializeDTO.RuleDTO rule) {
        return rule != null && rule.getDefaultCapacity() != null ? rule.getDefaultCapacity() : CAPACITY_FALLBACK;
    }

    private String versionKey(Integer dayOfWeek, LocalDate effectiveFrom) {
        return dayOfWeek + "|" + effectiveFrom;
    }

}
