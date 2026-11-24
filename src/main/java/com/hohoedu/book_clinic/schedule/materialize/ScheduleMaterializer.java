package com.hohoedu.book_clinic.schedule.materialize;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
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
    public static final int RESERVATION_OPEN_DAYS = 30; 

    /** 템플릿에 정원이 비어 있는 회차에 쓸 최소 기본값 */
    private static final int CAPACITY_FALLBACK = 10;

    private static final String TYPE_CLOSED = "CLOSED";
    private static final String TYPE_TIME_CHANGE = "TIME_CHANGE";
    private static final String TYPE_SLOT_CHANGE = "SLOT_CHANGE";

    private final SlotInstanceRepository slotInstanceRepository;

    /** 서당 병행 센터가 다음 달 운영스케줄을 여는 날(전월 19일 — 서당 청구일 20일보다 하루 먼저) */
    public static final int MONTHLY_OPEN_DAY = 19;

    /**
     * 이 센터의 예약 오픈 마지막 날(2026-11-19).
     *
     * 책방만 하는 센터는 오늘+RESERVATION_OPEN_DAYS 를 매일 굴린다. 서당 병행 센터(erp_center.with_seodang)는
     * 이용권이 달력 월 단위라 스케줄도 달 단위로 연다 — 18일까지는 이번 달 말일, 19일부터는 다음 달 말일.
     * 엔진·예약 목록·예약 확정·자동예약이 모두 이 값을 써야 "보이는데 예약은 안 되는" 날이 생기지 않는다.
     */
    public LocalDate openUntil(String centerCode) {
        LocalDate today = KstClock.today();
        if (!Boolean.TRUE.equals(slotInstanceRepository.isWithSeodang(centerCode))) {
            return today.plusDays(RESERVATION_OPEN_DAYS);
        }
        return monthlyOpenUntil(today);
    }

    /** 서당 병행 센터 오픈 마지막 날 — 18일까지는 이번 달 말일, 19일부터는 다음 달 말일 */
    static LocalDate monthlyOpenUntil(LocalDate today) {
        YearMonth ym = YearMonth.from(today);
        return (today.getDayOfMonth() >= MONTHLY_OPEN_DAY ? ym.plusMonths(1) : ym).atEndOfMonth();
    }

    /** 오늘~예약 오픈일 전체를 다시 찍는다. 일일 배치·수동 재계산용 */
    @Transactional
    public MaterializeDTO.ResultDTO materializeHorizon(String centerCode) {
        LocalDate today = KstClock.today();
        LocalDate until = openUntil(centerCode);
        // 오픈 기간 안 미운영일의 대체일은 오픈일 너머여도 함께 찍는다(2026-11-23) — ScheduleExceptionService.materializeSubstitute 참고
        List<LocalDate> dates = new ArrayList<>(datesBetween(today, until));
        substituteDates(centerCode, today, until).forEach((closedDay, substitute) -> {
            if (!closedDay.isBefore(today) && !closedDay.isAfter(until)) {
                dates.add(substitute);
            }
        });
        return run(centerCode, dates);
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
        LocalDate to = openUntil(centerCode);
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
        Map<LocalDate, List<MaterializeDTO.InstanceDTO>> desiredByDate = desiredSlots(centerCode, targets);

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

            List<MaterializeDTO.InstanceDTO> desired = desiredByDate.get(date);

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

    /**
     * 그날 찍힐 슬롯을 DB에 쓰지 않고 계산만 한다(2026-11-23) — 미운영 예외의 대체 운영일 검증용.
     * 오픈일 너머라 아직 slot_instance가 없는 날도 규칙·예외대로 열리는지 알 수 있다.
     */
    public List<MaterializeDTO.InstanceDTO> previewDay(String centerCode, LocalDate date) {
        return desiredSlots(centerCode, List.of(date)).get(date);
    }

    /**
     * 기간 안의 미운영 예외 중 대체 운영일이 있는 것 — 미운영일 → 대체일.
     * 반복 예약 미리보기가 그 주를 대체일로 옮겨 잡을 때 쓴다.
     */
    public Map<LocalDate, LocalDate> substituteDates(String centerCode, LocalDate from, LocalDate to) {
        Map<LocalDate, LocalDate> map = new HashMap<>();
        substituteExceptions(centerCode, from, to).forEach((closedDay, ex) -> map.put(closedDay, ex.getSubstituteDate()));
        return map;
    }

    /** 미운영일 → 대체 운영일이 있는 그 미운영 예외(사유 포함) — 반복 예약 미리보기가 안내 문구까지 내려줄 때 쓴다 */
    public Map<LocalDate, MaterializeDTO.ExceptionDTO> substituteExceptions(String centerCode, LocalDate from, LocalDate to) {
        Map<LocalDate, MaterializeDTO.ExceptionDTO> map = new HashMap<>();
        for (MaterializeDTO.ExceptionDTO ex : slotInstanceRepository.findExceptionsInRange(centerCode, from, to)) {
            if (TYPE_CLOSED.equals(ex.getExceptionType()) && ex.getSubstituteDate() != null) {
                map.put(ex.getStartDate(), ex);
            }
        }
        return map;
    }

    /** 날짜별 확정 슬롯 계산 — 입력값은 날짜별로 쪼개 조회하지 않고 기간 전체를 한 번에 읽는다 */
    private Map<LocalDate, List<MaterializeDTO.InstanceDTO>> desiredSlots(String centerCode, List<LocalDate> targets) {
        LocalDate from = targets.get(0);
        LocalDate to = targets.get(targets.size() - 1);

        // SlotInstanceMapper 주석 참고. 대체 운영일이 기간 안에 있는 미운영 예외도 함께 읽힌다
        List<MaterializeDTO.ExceptionDTO> exceptions =
                slotInstanceRepository.findExceptionsInRange(centerCode, from, to);

        // 대체 운영일은 미운영일의 규칙으로 찍으므로, 미운영일이 기간보다 뒤면 그 날까지의 규칙이 필요하다
        LocalDate ruleTo = exceptions.stream().map(MaterializeDTO.ExceptionDTO::getStartDate)
                .filter(to::isBefore).max(LocalDate::compareTo).orElse(to);

        Map<Integer, List<MaterializeDTO.RuleDTO>> rulesByDay = slotInstanceRepository
                .findRulesUpTo(centerCode, ruleTo).stream()
                .collect(Collectors.groupingBy(MaterializeDTO.RuleDTO::getDayOfWeek));

        Map<String, List<MaterializeDTO.TemplateSlotDTO>> slotsByVersion = new HashMap<>();
        for (MaterializeDTO.TemplateSlotDTO slot : slotInstanceRepository.findTemplateSlotsUpTo(centerCode, ruleTo)) {
            slotsByVersion.computeIfAbsent(versionKey(slot.getDayOfWeek(), slot.getEffectiveFrom()),
                    k -> new ArrayList<>()).add(slot);
        }

        Map<Integer, List<MaterializeDTO.ExceptionSlotDTO>> exceptionSlots = slotInstanceRepository
                .findExceptionSlotsInRange(centerCode, from, to).stream()
                .collect(Collectors.groupingBy(MaterializeDTO.ExceptionSlotDTO::getExceptionId));

        Map<LocalDate, List<MaterializeDTO.InstanceDTO>> result = new HashMap<>();
        for (LocalDate date : targets) {
            MaterializeDTO.RuleDTO rule = effectiveRule(rulesByDay.get(date.getDayOfWeek().getValue()), date);
            List<MaterializeDTO.InstanceDTO> slots = buildSlots(date, rule, slotsByVersion, exceptions, exceptionSlots);

            // 대체 운영일(2026-11-23) — 원래 열지 않는 날이면 미운영일의 요일 규칙 회차로 그날만 연다.
            // 이미 운영하는 날은 그날 회차를 그대로 둔다. 그날 자체가 미운영 예외면 buildSlots가 다시 막는다.
            LocalDate closedDay = substitutedFrom(date, exceptions);
            if (slots.isEmpty() && closedDay != null) {
                MaterializeDTO.RuleDTO closedRule =
                        effectiveRule(rulesByDay.get(closedDay.getDayOfWeek().getValue()), closedDay);
                slots = buildSlots(date, closedRule, slotsByVersion, exceptions, exceptionSlots);
            }
            result.put(date, slots);
        }
        return result;
    }

    /**
     * date가 대체 운영일이면 원래 미운영일, 아니면 null(2026-11-23). 대체 운영일은 예약 오픈·이용권
     * 유효기간을 미운영일 기준으로도 본다 — 12/31 미운영 → 1/2 대체면 12월 이용권으로 1/2를 쓸 수 있다.
     */
    public LocalDate closedDayOf(String centerCode, LocalDate date) {
        return substitutedFrom(date, slotInstanceRepository.findExceptionsInRange(centerCode, date, date));
    }

    /** date를 대체 운영일로 지정한 미운영 예외의 날짜 — 없으면 null */
    private LocalDate substitutedFrom(LocalDate date, List<MaterializeDTO.ExceptionDTO> exceptions) {
        return exceptions.stream()
                .filter(ex -> TYPE_CLOSED.equals(ex.getExceptionType()) && date.equals(ex.getSubstituteDate()))
                .map(MaterializeDTO.ExceptionDTO::getStartDate)
                .findFirst().orElse(null);
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

        List<MaterializeDTO.InstanceDTO> slots;
        if (rule == null || !Boolean.TRUE.equals(rule.getIsOpen())) {
            // 규칙이 없거나 휴무 요일 — 하루짜리 운영시간 변경 예외에 회차를 확정해 둔 날만 연다(2026-11-19).
            // 기간형은 열지 않는다: "12/20~27 운영시간 15~19시"가 그 사이 휴무 요일(일요일 등)까지
            // 열어버리면 안 되기 때문이다. 템플릿이 없으니 확정 회차 없이는 만들 회차도 없다.
            if (!isSingleDayTimeChange(period)) {
                return List.of();
            }
            List<MaterializeDTO.ExceptionSlotDTO> defined = definedSlots(exceptionSlots, period.getExceptionId());
            if (defined.isEmpty()) {
                return List.of();
            }
            slots = fromExceptionSlots(date, defined, rule);
        } else if (period != null && TYPE_TIME_CHANGE.equals(period.getExceptionType())) {
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

    /** 휴무 요일을 그날만 열 수 있는 예외인가 — 하루짜리 운영시간 변경 */
    static boolean isSingleDayTimeChange(MaterializeDTO.ExceptionDTO period) {
        return period != null
                && TYPE_TIME_CHANGE.equals(period.getExceptionType())
                && period.getStartDate().equals(period.getEndDate());
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
