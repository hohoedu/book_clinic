package com.hohoedu.book_clinic.reservation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.handler.exception.Exception401;
import com.hohoedu.book_clinic._core.handler.exception.Exception404;
import com.hohoedu.book_clinic._core.utils.KstClock;
import com.hohoedu.book_clinic.reservation._dto.ReservationReqDTO;
import com.hohoedu.book_clinic.reservation._dto.ReservationRespDTO;
import com.hohoedu.book_clinic.student.StudentRepository;
import com.hohoedu.book_clinic.student.model.Student;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 예약(erp_bookstore_reservation / slot_instance) — 등록·취소·4주 일괄 (2026-08-18).
 *
 * [동시성 원칙] "정원 확인"과 "정원 증가"를 절대 두 단계로 나누지 않는다. 두 단계로 나누면
 * 그 사이에 다른 요청이 끼어들어 정원을 초과해 받을 수 있다. 대신 조건부 UPDATE
 * (WHERE reserved_count &lt; capacity) 한 문장으로 확인과 증가를 묶어, DB 행 락이 동시 요청을
 * 한 명씩 순서대로 처리하게 만든다. 영향행수가 0이면 그 순간의 실제 값 기준으로 마감이라는 뜻이다.
 *
 * [4주 일괄 = 단건 예약의 반복] 별도 로직이 아니라 reserveOne()을 날짜 오름차순으로 여러 번
 * 부르는 것뿐이다. 하나의 @Transactional 안에서 실행하므로, 중간에 하나라도 실패하면 이미
 * 성공했던 앞선 슬롯의 증가분까지 전부 롤백된다. 잠금 순서를 날짜 오름차순으로 고정하는 이유는,
 * 겹치는 두 배치 요청이 서로 다른 순서로 슬롯을 잠그면 교착 상태에 빠질 수 있기 때문이다.
 *
 * [학생 앱 / 센터 직원 대리 예약 공용] 이 서비스는 호출 주체를 구분하지 않는다. 직원이 대신
 * 등록한다고 정원 체크를 건너뛰는 경로를 따로 두지 않는다 — 두 채널의 컨트롤러(ReservationController,
 * ReservationAdminController)가 studentId를 세션에서 가져오느냐 요청 본문에서 가져오느냐만 다를 뿐,
 * 그 뒤로는 완전히 같은 메서드를 부른다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    /** "월 = 4주"는 이 프로그램의 정책. 예약 오픈 기간도 여기 맞춘다 (28일) */
    private static final int BATCH_WEEKS = 4;

    private final ReservationRepository repository;
    private final StudentRepository studentRepository;

    // ── 조회 ─────────────────────────────────────────────────────────────

    /** 예약 가능한 열린 슬롯 목록. 기간 미지정 시 오늘부터 4주(28일) */
    public List<ReservationRespDTO.SlotOptionDTO> findOpenSlots(String studentId, LocalDate fromDate, LocalDate toDate) {
        Student student = requireStudent(studentId);
        LocalDate from = fromDate != null ? fromDate : KstClock.today();
        LocalDate to = toDate != null ? toDate : from.plusDays(BATCH_WEEKS * 7 - 1);
        if (to.isBefore(from)) {
            throw new Exception400("조회 기간이 올바르지 않습니다.");
        }
        return repository.findOpenSlots(student.getCenterCode(), from, to, studentId);
    }

    public List<ReservationRespDTO.ReservationItemDTO> findMyReservations(String studentId) {
        requireStudent(studentId);
        return repository.findMyReservations(studentId, KstClock.today());
    }

    /** 대리 예약 화면(관리자) — 특정 날짜의 예약 목록 */
    public List<ReservationRespDTO.AdminReservationRowDTO> findReservationsByDate(String centerCode, LocalDate date) {
        return repository.findReservationsByDate(centerCode, date);
    }

    /** 이름/appId로 학생 검색 — 대리 예약 화면의 학생 선택용 */
    public List<ReservationRespDTO.StudentSearchDTO> searchStudents(String keyword) {
        return studentRepository.searchByKeyword(keyword).stream()
                .map(this::toStudentSearchDTO)
                .toList();
    }

    // ── 출결 전환 (2026-08-18) ───────────────────────────────────────────

    /**
     * 입실 시점에 그날 예약을 ATTENDED로 전환한다. {@code MonitorService.enterSession}이 부른다.
     * 예약 없이 온 경우(도보 방문 등)엔 전환할 대상이 없으므로 조용히 넘어간다 — 예약이
     * 필수가 아닌 한 이걸 에러로 취급하면 입실 자체가 막혀버린다.
     *
     * 같은 학생이 하루에 두 번 로그인해도 안전하다 — transitionStatus가 RESERVED일 때만
     * 전환하므로, 이미 ATTENDED인 상태에서 다시 불려도 0건 갱신되고 로그도 중복으로 안 쌓인다.
     */
    @Transactional
    public void markAttended(String studentId, LocalDate serviceDate) {
        Long reservationId = repository.findReservedReservationIdByStudentAndDate(studentId, serviceDate);
        if (reservationId == null) {
            return;
        }
        int updated = repository.transitionStatus(reservationId, "RESERVED", "ATTENDED");
        if (updated > 0) {
            repository.insertLog(reservationId, "RESERVED", "ATTENDED", studentId, "STUDENT", null);
        }
    }

    /**
     * 노쇼 배치 — 슬롯 종료 시각(ends_at)이 지났는데 아직 RESERVED로 남은 예약을 NOSHOW로
     * 전환한다. {@code ReservationNoShowJob}이 매일 새벽 호출한다.
     *
     * @return 실제로 전환된 건수
     */
    @Transactional
    public int markNoShows(LocalDateTime asOf) {
        List<Long> candidates = repository.findNoShowCandidates(asOf);
        int count = 0;
        for (Long reservationId : candidates) {
            int updated = repository.transitionStatus(reservationId, "RESERVED", "NOSHOW");
            if (updated > 0) {
                repository.insertLog(reservationId, "RESERVED", "NOSHOW", null, "SYSTEM", "미입실 자동 처리");
                count++;
            }
        }
        return count;
    }

    // ── 단건 예약 ────────────────────────────────────────────────────────

    @Transactional
    public ReservationRespDTO.ReservationItemDTO reserve(String studentId, Long slotInstanceId) {
        if (slotInstanceId == null) {
            throw new Exception400("예약할 회차를 선택해주세요.");
        }
        Student student = requireStudent(studentId);
        return reserveOne(student, slotInstanceId);
    }

    @Transactional
    public void cancel(String studentId, Long reservationId, String reason) {
        if (reservationId == null) {
            throw new Exception400("취소할 예약을 선택해주세요.");
        }
        requireStudent(studentId);

        Long slotInstanceId = repository.findSlotInstanceIdByReservationId(reservationId, studentId);
        if (slotInstanceId == null) {
            throw new Exception404("예약을 찾을 수 없습니다.");
        }

        int updated = repository.cancelReservation(reservationId, studentId, reason);
        if (updated == 0) {
            throw new Exception400("이미 취소되었거나 처리할 수 없는 예약입니다.");
        }

        repository.decrementReservedCount(slotInstanceId);
        repository.insertLog(reservationId, "RESERVED", "CANCELED", studentId, "STUDENT", reason);

        log.info("[예약] 취소 — reservationId={}, studentId={}, reason={}", reservationId, studentId, reason);
    }

    // ── 4주 일괄 ─────────────────────────────────────────────────────────

    /**
     * 4주 일괄 신청 미리보기. dayOfWeek(ISO 1=월~7=일)와 seq로 오늘 이후 가장 가까운 4번의
     * 해당 요일 날짜를 찾아, 각 날짜의 슬롯 상태를 알려준다. 실제 예약은 만들지 않는다(읽기 전용).
     */
    public List<ReservationRespDTO.BatchPreviewItemDTO> previewBatch(String studentId, Integer dayOfWeek, Integer seq) {
        Student student = requireStudent(studentId);
        if (dayOfWeek == null || dayOfWeek < 1 || dayOfWeek > 7) {
            throw new Exception400("요일 값이 올바르지 않습니다.");
        }
        if (seq == null) {
            throw new Exception400("회차를 선택해주세요.");
        }

        List<LocalDate> targetDates = nextDates(dayOfWeek, BATCH_WEEKS);
        List<ReservationRespDTO.SlotOptionDTO> slots =
                repository.findSlotsByDates(student.getCenterCode(), targetDates, seq, studentId);
        Map<LocalDate, ReservationRespDTO.SlotOptionDTO> byDate = slots.stream()
                .collect(Collectors.toMap(ReservationRespDTO.SlotOptionDTO::getServiceDate, s -> s));

        List<ReservationRespDTO.BatchPreviewItemDTO> result = new ArrayList<>();
        for (LocalDate date : targetDates) {
            ReservationRespDTO.SlotOptionDTO slot = byDate.get(date);
            ReservationRespDTO.BatchPreviewItemDTO item = new ReservationRespDTO.BatchPreviewItemDTO();
            item.setServiceDate(date);
            item.setSeq(seq);

            if (slot == null) {
                item.setTargetStatus("NOT_OPEN");
            } else {
                item.setSlotInstanceId(slot.getSlotInstanceId());
                item.setStartsAt(slot.getStartsAt());
                item.setEndsAt(slot.getEndsAt());
                if (Boolean.TRUE.equals(slot.getReservedByMe())) {
                    item.setTargetStatus("ALREADY_RESERVED");
                } else if (!"OPEN".equals(slot.getStatus())) {
                    item.setTargetStatus("CLOSED");
                } else if (slot.getReservedCount() != null && slot.getCapacity() != null
                        && slot.getReservedCount() >= slot.getCapacity()) {
                    item.setTargetStatus("FULL");
                } else if (repository.countOtherReservedOnDate(studentId, date, slot.getSlotInstanceId()) > 0) {
                    // 하루 1회차만 정책 — 이 날짜에 이미 다른 회차를 예약해뒀으면 이 요일·회차
                    // 조합으로는 이 주차를 신청할 수 없다(reserveOne이 실제 확정 시점에도 다시 막는다)
                    item.setTargetStatus("DAY_CONFLICT");
                } else {
                    item.setTargetStatus("OPEN");
                }
            }
            result.add(item);
        }
        return result;
    }

    /**
     * 4주 일괄 확정. 클라이언트가 batch-preview로 확인한 뒤 실제로 신청할 슬롯 id만 골라 보낸다.
     * 하나라도 그 사이 마감되면 전체를 거부한다 — "이 시간은 매주 내 자리"라는 약속을 지키기
     * 위해서다. 실패 지점은 구체적으로("9월 23일 목요일 2회차는 마감되었습니다") 안내한다.
     */
    @Transactional
    public List<ReservationRespDTO.ReservationItemDTO> reserveBatch(String studentId, List<Long> slotInstanceIds) {
        if (slotInstanceIds == null || slotInstanceIds.isEmpty()) {
            throw new Exception400("예약할 회차를 선택해주세요.");
        }
        Student student = requireStudent(studentId);

        List<ReservationRespDTO.SlotOptionDTO> targets = repository.findSlotsByIds(slotInstanceIds);
        Map<Long, ReservationRespDTO.SlotOptionDTO> byId = targets.stream()
                .collect(Collectors.toMap(ReservationRespDTO.SlotOptionDTO::getSlotInstanceId, s -> s));

        // 겹치는 배치 요청끼리 교착에 빠지지 않도록 항상 날짜 오름차순으로 잠근다
        List<Long> ordered = slotInstanceIds.stream().distinct()
                .sorted(Comparator.comparing(id -> {
                    ReservationRespDTO.SlotOptionDTO s = byId.get(id);
                    return s != null ? s.getServiceDate() : LocalDate.MAX;
                }))
                .toList();

        List<ReservationRespDTO.ReservationItemDTO> reserved = new ArrayList<>();
        for (Long slotInstanceId : ordered) {
            reserved.add(reserveOne(student, slotInstanceId));
        }

        log.info("[예약] 4주 일괄 확정 — studentId={}, slotInstanceIds={}", studentId, ordered);
        return reserved;
    }

    // ── 내부 ─────────────────────────────────────────────────────────────

    private ReservationRespDTO.ReservationItemDTO reserveOne(Student student, Long slotInstanceId) {
        ReservationRespDTO.SlotOptionDTO slot = repository.findSlotOptionById(slotInstanceId);
        if (slot == null) {
            throw new Exception404("존재하지 않는 회차입니다.");
        }

        // 하루 1회차만 정책(2026-08-18) — 4주 일괄(reserveOne을 날짜별로 반복 호출)로 들어와도
        // 그대로 적용된다. 같은 슬롯 재예약 시도는 여기서 걸리지 않고 DuplicateKeyException으로 빠진다.
        if (repository.countOtherReservedOnDate(student.getStudentId(), slot.getServiceDate(), slotInstanceId) > 0) {
            throw new Exception400(slotLabel(slot.getServiceDate(), slot.getSeq())
                    + " — 이 날짜엔 이미 다른 회차를 예약하셨습니다. 하루에 한 회차만 예약할 수 있습니다.");
        }

        int updated = repository.incrementReservedCount(slotInstanceId);
        if (updated == 0) {
            throw new Exception400(slotLabel(slot.getServiceDate(), slot.getSeq()) + "는 마감되었습니다.");
        }

        ReservationReqDTO.InsertReservationDTO command = new ReservationReqDTO.InsertReservationDTO();
        command.setSlotInstanceId(slotInstanceId);
        command.setStudentId(student.getStudentId());
        try {
            repository.insertReservation(command);
        } catch (DuplicateKeyException e) {
            // UX_reservation_slot_student가 막은 것 — 조회 시점과 등록 시점 사이의 경합(TOCTOU)도 여기서 걸린다
            throw new Exception400("이미 예약된 회차입니다.");
        }

        repository.insertLog(command.getReservationId(), null, "RESERVED", student.getStudentId(), "STUDENT", null);
        return repository.findReservationById(command.getReservationId());
    }

    private Student requireStudent(String studentId) {
        Student student = studentRepository.findById(studentId);
        if (student == null) {
            throw new Exception401("로그인이 필요합니다.");
        }
        return student;
    }

    private ReservationRespDTO.StudentSearchDTO toStudentSearchDTO(Student student) {
        ReservationRespDTO.StudentSearchDTO dto = new ReservationRespDTO.StudentSearchDTO();
        dto.setStudentId(student.getStudentId());
        dto.setStudentName(student.getStudentName());
        dto.setSchool(student.getSchool());
        dto.setGradeKey(student.getGradeKey());
        dto.setAppId(student.getAppId());
        return dto;
    }

    /** 오늘(KST) 이후 가장 가까운 dayOfWeek부터 시작해 매주 반복되는 날짜 count개 */
    private List<LocalDate> nextDates(int dayOfWeek, int count) {
        LocalDate date = KstClock.today();
        while (date.getDayOfWeek().getValue() != dayOfWeek) {
            date = date.plusDays(1);
        }
        List<LocalDate> dates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            dates.add(date.plusWeeks(i));
        }
        return dates;
    }

    private String slotLabel(LocalDate date, Integer seq) {
        return String.format("%d월 %d일 %s %d회차", date.getMonthValue(), date.getDayOfMonth(), dayLabel(date), seq);
    }

    private String dayLabel(LocalDate date) {
        return switch (date.getDayOfWeek().getValue()) {
            case 1 -> "월요일";
            case 2 -> "화요일";
            case 3 -> "수요일";
            case 4 -> "목요일";
            case 5 -> "금요일";
            case 6 -> "토요일";
            default -> "일요일";
        };
    }

}
