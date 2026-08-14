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
import com.hohoedu.book_clinic._core.utils.KstClock;
import com.hohoedu.book_clinic.schedule._dto.ScheduleReqDTO;
import com.hohoedu.book_clinic.schedule._dto.ScheduleRespDTO;

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
 * [여기서 슬롯을 찍지 않는 이유] 실제 예약이 붙는 대상은 slot_instance이고 그건 materialize()가
 * 만든다. 이 클래스는 그 입력값인 규칙만 다룬다 — materialize() 연동은 다음 작업.
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

        Map<Integer, List<LocalDate>> upcomingByDay =
                scheduleRepository.findUpcomingVersions(centerCode, base)
                        .stream().collect(Collectors.groupingBy(ScheduleRespDTO.VersionDTO::getDayOfWeek,
                                Collectors.mapping(ScheduleRespDTO.VersionDTO::getEffectiveFrom, Collectors.toList())));

        List<ScheduleRespDTO.DayDTO> days = new ArrayList<>();
        for (int dow = 1; dow <= 7; dow++) {
            ScheduleRespDTO.DayDTO day = saved.get(dow);
            if (day == null) {
                day = emptyDay(dow);
            }
            day.setSlots(slotsByDay.getOrDefault(dow, List.of()));
            day.setUpcomingVersions(upcomingByDay.getOrDefault(dow, List.of()));
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

}
