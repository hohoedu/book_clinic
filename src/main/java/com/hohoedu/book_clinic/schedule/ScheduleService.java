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
import com.hohoedu.book_clinic.schedule.materialize.ScheduleMaterializer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 운영 스케줄 — 요일별 운영 규칙의 조회와 저장.
 *
 * [버전(effective_from)이 왜 필요한가] 스케줄은 "지금부터 이렇게 바꾼다"가 아니라 "다음 달
 * 1일부터 이렇게 바꾼다"로 쓰이는 일이 훨씬 많다. 기존 행을 UPDATE 해버리면 예고 저장을 못 하고,
 * 지나간 날의 회차가 실제로 몇 시였는지도 사라진다. 그래서 저장은 항상 (센터, 요일, 적용시작일)
 * 세대를 새로 만들고, 조회는 "그 날짜 이하의 적용시작일 중 최댓값"을 고른다.
 *
 * [규칙과 슬롯의 분리] 실제 예약이 붙는 대상은 slot_instance이고 그건 ScheduleMaterializer가
 * 만든다. 이 클래스는 그 입력값인 규칙만 다루되, 규칙을 저장한 뒤에는 곧바로 엔진을 호출해
 * 예약 오픈 기간 안의 해당 요일을 다시 찍는다 — 규칙만 바뀌고 슬롯이 옛날 그대로면 관리자가
 * 화면에서 본 것과 학생이 예약하는 시간이 어긋난다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private static final int SLOT_MINUTES_DEFAULT = 50;
    private static final int CAPACITY_DEFAULT = 10;
    /** 회차 1개가 가질 수 있는 최대 인원 — 오타로 999명이 들어가는 것만 막는 상한 */
    private static final int CAPACITY_MAX = 200;

    private final ScheduleRepository scheduleRepository;
    private final ScheduleMaterializer scheduleMaterializer;

    /**
     * 요일별 스케줄 조회. baseDate가 null이면 오늘(KST) 기준.
     *
     * 저장된 적 없는 요일도 기본값으로 채워 항상 7개를 반환한다 — 화면이 월~일 탭을 고정으로
     * 갖고 있어서 빠진 요일이 있으면 탭 하나가 빈 채로 남는다.
     */
    public ScheduleRespDTO.WeekDTO findWeek(String centerCode, LocalDate baseDate) {
        LocalDate base = baseDate != null ? baseDate : KstClock.today();

        Map<Integer, ScheduleRespDTO.DayDTO> saved = scheduleRepository.findEffectiveDays(centerCode, base)
                .stream().collect(Collectors.toMap(ScheduleRespDTO.DayDTO::getDayOfWeek, d -> d));

        Map<Integer, List<ScheduleRespDTO.SlotDTO>> slotsByDay =
                scheduleRepository.findEffectiveSlots(centerCode, base)
                        .stream().collect(Collectors.groupingBy(ScheduleRespDTO.SlotDTO::getDayOfWeek));

        List<ScheduleRespDTO.DayDTO> days = new ArrayList<>();
        for (int dow = 1; dow <= 7; dow++) {
            ScheduleRespDTO.DayDTO day = saved.get(dow);
            if (day == null) {
                day = emptyDay(dow);
            }
            day.setSlots(slotsByDay.getOrDefault(dow, List.of()));
            days.add(day);
        }

        ScheduleRespDTO.WeekDTO week = new ScheduleRespDTO.WeekDTO();
        week.setCenterCode(centerCode);
        week.setBaseDate(base);
        week.setDays(days);
        return week;
    }

    /** 아직 저장된 적 없는 요일 — 휴무 + 화면 기본값 */
    private ScheduleRespDTO.DayDTO emptyDay(int dayOfWeek) {
        ScheduleRespDTO.DayDTO day = new ScheduleRespDTO.DayDTO();
        day.setDayOfWeek(dayOfWeek);
        day.setEffectiveFrom(null);
        day.setIsOpen(false);
        day.setSlotMinutes(SLOT_MINUTES_DEFAULT);
        day.setBreakMinutes(0);
        day.setDefaultCapacity(CAPACITY_DEFAULT);
        return day;
    }

    /**
     * 요일별 스케줄 저장. 요청에 담긴 요일만 새 버전으로 만든다.
     *
     * 같은 적용시작일로 다시 저장하면 그 버전을 덮어쓴다(원본은 _del에 UPDATE 스냅샷). 저장을
     * 누를 때마다 버전이 하나씩 쌓이면 "8/20부터"를 세 번 고쳤을 때 어느 게 진짜인지 알 수 없게
     * 되기 때문에, 같은 날짜는 언제나 한 버전만 존재하게 한다.
     *
     * @return 실제로 적용된 시작일
     */
    @Transactional
    public LocalDate saveWeek(String centerCode, String userId, ScheduleReqDTO.SaveWeekReqDTO req) {
        LocalDate today = KstClock.today();
        LocalDate effectiveFrom = Boolean.TRUE.equals(req.getApplyNow()) ? today : req.getEffectiveFrom();

        if (effectiveFrom == null) {
            throw new Exception400("적용 시작일을 선택하거나 즉시적용을 체크해주세요.");
        }
        if (effectiveFrom.isBefore(today)) {
            throw new Exception400("적용 시작일은 오늘 이후로만 지정할 수 있습니다.");
        }

        // 검증을 전부 끝낸 뒤에 쓰기를 시작한다 — 5번째 요일에서 걸려 롤백되면
        // 화면은 "저장 실패"만 보고 어느 요일이 문제인지 알 수 없다.
        Set<Integer> seen = new HashSet<>();
        for (ScheduleReqDTO.DayReqDTO day : req.getDays()) {
            if (!seen.add(day.getDayOfWeek())) {
                throw new Exception400("같은 요일이 두 번 들어왔습니다.");
            }
            normalizeAndValidate(day);
        }

        for (ScheduleReqDTO.DayReqDTO day : req.getDays()) {
            int reserved = scheduleRepository.countAffectedReservations(
                    centerCode, day.getDayOfWeek(), effectiveFrom);
            if (reserved > 0) {
                throw new Exception400(dayLabel(day.getDayOfWeek())
                        + "에 이미 " + reserved + "건의 예약이 있어 변경할 수 없습니다. 예약을 확인한 뒤 다시 시도해주세요.");
            }
        }

        for (ScheduleReqDTO.DayReqDTO day : req.getDays()) {
            int dow = day.getDayOfWeek();

            if (scheduleRepository.existsVersion(centerCode, dow, effectiveFrom)) {
                scheduleRepository.archiveScheduleSlots(centerCode, dow, effectiveFrom, "UPDATE", userId);
                scheduleRepository.archiveSchedule(centerCode, dow, effectiveFrom, "UPDATE", userId);
                scheduleRepository.deleteVersion(centerCode, dow, effectiveFrom);
            }

            scheduleRepository.insertSchedule(centerCode, effectiveFrom, day, userId);

            if (Boolean.TRUE.equals(day.getIsOpen())) {
                scheduleRepository.insertSlots(centerCode, dow, effectiveFrom, day.getSlots());
            }
        }

        // 저장한 요일만 다시 찍는다. 바로 위에서 예약이 걸린 요일은 이미 막았으므로 여기서
        // blockedDates가 나올 일은 없지만, 나오면 규칙과 슬롯이 어긋난 상태라 로그로 남겨둔다.
        for (Integer dow : seen) {
            MaterializeDTO.ResultDTO result =
                    scheduleMaterializer.materializeDayOfWeek(centerCode, dow, effectiveFrom);
            if (!result.getBlockedDates().isEmpty()) {
                log.warn("[운영스케줄] 규칙은 저장됐지만 슬롯을 갱신하지 못한 날짜 — center={}, dow={}, dates={}",
                        centerCode, dow, result.getBlockedDates());
            }
        }

        log.info("[운영스케줄] 저장 — center={}, effectiveFrom={}, days={}, by={}",
                centerCode, effectiveFrom, seen, userId);
        return effectiveFrom;
    }

    /**
     * 요일 1개의 값 보정 + 검증. 보정과 검증을 한 메서드에 둔 이유는 "비어 있으면 기본값"과
     * "값이 이상하면 거부"가 같은 필드에 대한 판단이라 떨어뜨리면 순서가 어긋나기 쉬워서다.
     */
    private void normalizeAndValidate(ScheduleReqDTO.DayReqDTO day) {
        Integer dow = day.getDayOfWeek();
        if (dow == null || dow < 1 || dow > 7) {
            throw new Exception400("요일 값이 올바르지 않습니다.");
        }
        String label = dayLabel(dow);

        if (day.getSlotMinutes() == null) day.setSlotMinutes(SLOT_MINUTES_DEFAULT);
        if (day.getBreakMinutes() == null) day.setBreakMinutes(0);
        if (day.getDefaultCapacity() == null) day.setDefaultCapacity(CAPACITY_DEFAULT);

        if (day.getSlotMinutes() <= 0) {
            throw new Exception400(label + " 회차 기본시간은 1분 이상이어야 합니다.");
        }
        if (day.getBreakMinutes() < 0) {
            throw new Exception400(label + " 회차 간격은 0분 이상이어야 합니다.");
        }
        if (day.getDefaultCapacity() < 1 || day.getDefaultCapacity() > CAPACITY_MAX) {
            throw new Exception400(label + " 최대 예약 인원은 1~" + CAPACITY_MAX + "명 사이여야 합니다.");
        }

        // 휴무는 운영시간·회차를 아예 갖지 않는다. 화면에서 토글만 끄고 값을 그대로 보내와도
        // 여기서 비워야 나중에 "휴무인데 회차가 남아 있는" 버전이 생기지 않는다.
        if (!Boolean.TRUE.equals(day.getIsOpen())) {
            day.setIsOpen(false);
            day.setOpenTime(null);
            day.setCloseTime(null);
            day.setSlots(List.of());
            return;
        }

        LocalTime open = day.getOpenTime();
        LocalTime close = day.getCloseTime();
        if (open == null || close == null) {
            throw new Exception400(label + " 운영 시작·종료 시각을 입력해주세요.");
        }
        if (!open.isBefore(close)) {
            throw new Exception400(label + " 운영 종료 시각은 시작 시각보다 늦어야 합니다.");
        }

        List<ScheduleReqDTO.SlotReqDTO> slots = day.getSlots();
        if (slots == null || slots.isEmpty()) {
            throw new Exception400(label + " 회차를 1개 이상 등록해주세요.");
        }

        // 화면의 드래그 정렬은 표시 순서일 뿐이라 시작시각 순서와 다를 수 있다. 겹침 판정과
        // seq 부여 모두 시간 순서를 기준으로 해야 맞으므로 여기서 한 번 정렬해 두고,
        // 정렬된 이 리스트 순서 그대로 insertSlots가 seq 1..n을 매긴다.
        slots.sort(Comparator.comparing(ScheduleReqDTO.SlotReqDTO::getStartTime));

        LocalTime prevEnd = null;
        for (int i = 0; i < slots.size(); i++) {
            ScheduleReqDTO.SlotReqDTO slot = slots.get(i);
            String slotLabel = label + " " + (i + 1) + "회차";

            if (!slot.getStartTime().isBefore(slot.getEndTime())) {
                throw new Exception400(slotLabel + "의 종료 시각은 시작 시각보다 늦어야 합니다.");
            }
            if (slot.getStartTime().isBefore(open) || slot.getEndTime().isAfter(close)) {
                throw new Exception400(slotLabel + "가 운영 시간을 벗어납니다.");
            }
            if (prevEnd != null && slot.getStartTime().isBefore(prevEnd)) {
                throw new Exception400(slotLabel + "의 시간이 앞 회차와 겹칩니다.");
            }
            if (slot.getCapacity() == null) {
                slot.setCapacity(day.getDefaultCapacity());
            }
            if (slot.getCapacity() < 1 || slot.getCapacity() > CAPACITY_MAX) {
                throw new Exception400(slotLabel + " 인원은 1~" + CAPACITY_MAX + "명 사이여야 합니다.");
            }
            prevEnd = slot.getEndTime();
        }
    }

    private String dayLabel(int dayOfWeek) {
        return switch (dayOfWeek) {
            case 1 -> "월요일";
            case 2 -> "화요일";
            case 3 -> "수요일";
            case 4 -> "목요일";
            case 5 -> "금요일";
            case 6 -> "토요일";
            case 7 -> "일요일";
            default -> dayOfWeek + "요일";
        };
    }

    // ── 스케줄 변경 예약 이력 ─────────────────────────────────────────────

    /**
     * 버전 목록. 화면 오른쪽 "스케줄 변경 예약 이력" 패널용.
     *
     * [기간이 어떻게 나오나] 테이블에는 "언제부터"만 있다. 종료일은 다음 버전 시작일에서 하루를
     * 뺀 값으로 만든다 — 종료일을 컬럼으로 들고 있으면 새 버전을 저장할 때마다 직전 버전을 같이
     * 고쳐야 하고, 두 값이 어긋나면 어느 쪽이 진실인지 알 수 없게 된다.
     *
     * [요일 구성은 "그 시점의 주간 전체"다] 이 버전에서 바뀐 요일만 보여주지 않는다. 관리자가
     * 궁금한 것은 "그날부터 우리 학원이 어떻게 도는가"이지 "그때 무엇을 눌렀는가"가 아니다.
     * 그래서 요일마다 그 시점에 유효한 버전(effective_from <= 기준일 중 최대)을 각각 골라 합친다.
     */
    public List<ScheduleRespDTO.VersionSummaryDTO> findVersions(String centerCode) {
        List<ScheduleRespDTO.VersionDayDTO> rows = scheduleRepository.findAllVersionDays(centerCode);
        if (rows.isEmpty()) {
            return List.of();
        }

        List<LocalDate> boundaries = rows.stream()
                .map(ScheduleRespDTO.VersionDayDTO::getEffectiveFrom)
                .distinct().sorted().toList();

        LocalDate today = KstClock.today();
        List<ScheduleRespDTO.VersionSummaryDTO> versions = new ArrayList<>();

        for (int i = 0; i < boundaries.size(); i++) {
            LocalDate start = boundaries.get(i);
            LocalDate end = (i + 1 < boundaries.size()) ? boundaries.get(i + 1).minusDays(1) : null;

            ScheduleRespDTO.VersionSummaryDTO version = new ScheduleRespDTO.VersionSummaryDTO();
            version.setEffectiveFrom(start);
            version.setEndDate(end);

            List<Integer> openDays = new ArrayList<>();
            for (int dow = 1; dow <= 7; dow++) {
                if (Boolean.TRUE.equals(effectiveOpenAt(rows, dow, start))) {
                    openDays.add(dow);
                }
            }
            version.setOpenDays(openDays);
            version.setOpenDayLabel(openDays.stream().map(this::shortDayLabel).collect(Collectors.joining(", ")));

            String status = start.isAfter(today) ? "UPCOMING"
                    : (end != null && end.isBefore(today)) ? "PAST" : "CURRENT";
            version.setStatus(status);
            version.setStatusLabel(switch (status) {
                case "UPCOMING" -> "예정";
                case "PAST" -> "이전";
                default -> "운영중";
            });
            // 이미 지나갔거나 지금 돌고 있는 버전은 기록이라 지울 수 없다
            version.setDeletable("UPCOMING".equals(status));

            versions.add(version);
        }

        // 화면 순서: 운영중 → 예정(가까운 순) → 이전(최근 순)
        versions.sort(Comparator
                .comparingInt((ScheduleRespDTO.VersionSummaryDTO v) -> switch (v.getStatus()) {
                    case "CURRENT" -> 0;
                    case "UPCOMING" -> 1;
                    default -> 2;
                })
                .thenComparing(v -> "PAST".equals(v.getStatus())
                        ? LocalDate.MAX.toEpochDay() - v.getEffectiveFrom().toEpochDay()
                        : v.getEffectiveFrom().toEpochDay()));
        return versions;
    }

    /** 기준일에 그 요일이 운영인지 — 요일별로 effective_from <= 기준일 중 가장 늦은 버전을 고른다 */
    private Boolean effectiveOpenAt(List<ScheduleRespDTO.VersionDayDTO> rows, int dayOfWeek, LocalDate baseDate) {
        Boolean open = null;
        for (ScheduleRespDTO.VersionDayDTO row : rows) {   // 조회가 effective_from 오름차순이라 마지막 통과분이 최신
            if (row.getDayOfWeek() == dayOfWeek && !row.getEffectiveFrom().isAfter(baseDate)) {
                open = row.getIsOpen();
            }
        }
        return open;
    }

    /**
     * 예정 버전 삭제. 지우면 그 기간은 이전 버전 규칙으로 되돌아간다.
     *
     * 수정 API는 없다(결정 2) — 예정 버전을 고치는 것은 같은 적용시작일로 다시 저장하면 되고,
     * 그건 saveWeek가 덮어쓰기로 처리한다.
     */
    @Transactional
    public void deleteVersion(String centerCode, String userId, LocalDate effectiveFrom) {
        LocalDate today = KstClock.today();
        if (!effectiveFrom.isAfter(today)) {
            throw new Exception400("아직 적용되지 않은 예정 버전만 삭제할 수 있습니다.");
        }

        List<Integer> dayOfWeeks = scheduleRepository.findVersionDayOfWeeks(centerCode, effectiveFrom);
        if (dayOfWeeks.isEmpty()) {
            throw new Exception404("해당 버전을 찾을 수 없습니다.");
        }

        // 결정 4 — 예약이 걸린 요일은 손대지 않는다. 삭제도 슬롯을 다시 찍는 일이라 저장과 같은 기준이다.
        for (Integer dow : dayOfWeeks) {
            int reserved = scheduleRepository.countAffectedReservations(centerCode, dow, effectiveFrom);
            if (reserved > 0) {
                throw new Exception400(dayLabel(dow) + "에 이미 " + reserved
                        + "건의 예약이 있어 삭제할 수 없습니다. 예약을 확인한 뒤 다시 시도해주세요.");
            }
        }

        for (Integer dow : dayOfWeeks) {
            scheduleRepository.archiveScheduleSlots(centerCode, dow, effectiveFrom, "DELETE", userId);
            scheduleRepository.archiveSchedule(centerCode, dow, effectiveFrom, "DELETE", userId);
            scheduleRepository.deleteVersion(centerCode, dow, effectiveFrom);
        }

        // 지운 뒤에 다시 찍어야 이전 버전 규칙으로 복구된다 (순서가 바뀌면 지운 버전이 그대로 반영된다)
        for (Integer dow : dayOfWeeks) {
            scheduleMaterializer.materializeDayOfWeek(centerCode, dow, effectiveFrom);
        }

        log.info("[운영스케줄] 예정 버전 삭제 — center={}, effectiveFrom={}, days={}, by={}",
                centerCode, effectiveFrom, dayOfWeeks, userId);
    }

    private String shortDayLabel(int dayOfWeek) {
        return dayLabel(dayOfWeek).substring(0, 1);
    }

}
