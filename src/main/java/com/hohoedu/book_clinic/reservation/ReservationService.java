package com.hohoedu.book_clinic.reservation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
import com.hohoedu.book_clinic.pass.PassService;
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
    private final PassService passService;

    // ── 조회 ─────────────────────────────────────────────────────────────

    /** 예약 가능한 열린 슬롯 목록(학생 앱). 기간 미지정 시 오늘부터 4주(28일) */
    public List<ReservationRespDTO.SlotOptionDTO> findOpenSlots(String studentId, LocalDate fromDate, LocalDate toDate) {
        Student student = requireStudent(studentId);
        return findOpenSlotsByCenter(student.getCenterCode(), fromDate, toDate, studentId);
    }

    /**
     * 예약 가능한 열린 슬롯 목록(센터 직원 대리 예약 화면 전용). 등록 패널은 학생을 고르기 전에도
     * 회차 목록부터 보여줘야 해서, studentId로 학생을 조회해 센터를 알아내는 학생 앱용 경로를
     * 그대로 쓸 수 없다 — studentId가 아직 빈 값일 수 있기 때문이다(위 findOpenSlots가 빈 값이면
     * "로그인이 필요합니다"를 던져 오작동했던 지점). 대신 로그인한 직원의 센터를 그대로 쓴다.
     * studentId는 비어 있어도 되며, 값이 있으면 그 학생이 이미 예약한 슬롯 표시(reservedByMe)에만 쓰인다.
     */
    public List<ReservationRespDTO.SlotOptionDTO> findOpenSlotsByCenter(String centerCode, LocalDate fromDate, LocalDate toDate, String studentId) {
        LocalDate from = fromDate != null ? fromDate : KstClock.today();
        LocalDate to = toDate != null ? toDate : from.plusDays(BATCH_WEEKS * 7 - 1);
        if (to.isBefore(from)) {
            throw new Exception400("조회 기간이 올바르지 않습니다.");
        }
        return repository.findOpenSlots(centerCode, from, to, studentId == null ? "" : studentId);
    }

    public List<ReservationRespDTO.ReservationItemDTO> findMyReservations(String studentId) {
        requireStudent(studentId);
        return repository.findMyReservations(studentId, KstClock.today());
    }

    /**
     * 오늘 예약(RESERVED/ATTENDED)이 있는지 — 로그인 단계 차단용(2026-08-20, 예약 필수 정책).
     * markAttended와 같은 조회를 읽기 전용으로 재사용한다. 회차 시간대까지는 여기서 안 본다 —
     * "예약 자체가 있는지"만 로그인 단계에서 거르고, 시간대 검증은 실제 입실 시점(markAttended)의
     * 몫으로 남겨둔다(로그인 시점엔 아직 회차 시간이 안 됐어도 로그인 자체는 막을 이유가 없다).
     */
    public boolean hasReservationToday(String studentId) {
        return repository.findReservedSlotByStudentAndDate(studentId, KstClock.today()) != null;
    }

    /** 대리 예약 화면(관리자) — 특정 날짜의 예약 목록 */
    public List<ReservationRespDTO.AdminReservationRowDTO> findReservationsByDate(String centerCode, LocalDate date) {
        return repository.findReservationsByDate(centerCode, date);
    }

    /** 대리 예약 화면(관리자) — 특정 날짜의 회차별 정원 요약(회차 카드) */
    public List<ReservationRespDTO.SlotOptionDTO> findSlotSummary(String centerCode, LocalDate date) {
        return repository.findSlotsByDate(centerCode, date);
    }

    /** 이름/appId로 학생 검색 — 대리 예약 화면의 학생 선택용. 로그인한 직원의 센터로 한정한다 */
    public List<ReservationRespDTO.StudentSearchDTO> searchStudents(String centerCode, String keyword) {
        return studentRepository.searchByKeyword(centerCode, keyword).stream()
                .map(this::toStudentSearchDTO)
                .toList();
    }

    // ── 출결 전환 (2026-08-18) ───────────────────────────────────────────

    /** 입실 허용 시작 시각 = 회차 시작 시각의 이만큼 전부터(2026-08-20, 조기 도착 허용). 정책 확정 전 수치라 상수 하나로 뺐다 — 나중에 값만 바꾸면 된다 */
    private static final long EARLY_ENTRY_MINUTES = 10;

    /**
     * 입실 시점에 그날 예약을 ATTENDED로 전환한다. {@code MonitorService.enterSession}이 부른다.
     *
     * [예약 필수 정책(2026-08-20)] 오늘 예약(RESERVED/ATTENDED) 자체가 없으면 입실을 막는다 —
     * 예약 없이 온 도보 방문을 허용하던 이전 정책이 뒤집혔다. 다만 이미 ATTENDED가 하나라도
     * 있으면(같은 학생이 하루에 두 번 로그인) "예약 없음"이 아니라 재로그인이므로 조용히 통과시킨다.
     *
     * [입실 1회 = 그날 예약 전부 출석(2026-08-28)] 입실/퇴실은 1일 1회뿐인데 하루에 2회차까지
     * 예약할 수 있어서, 두 번째 회차는 시간대가 돼도 따로 입실할 방법이 없다. 그래서 첫 입실 한 번으로
     * 그날 RESERVED인 회차를 모두 ATTENDED로 올린다 — 두 번째 회차 시간대가 아직 아니어도 출석으로 친다.
     *
     * 다만 "아무 때나 입실"은 여전히 막는다(2026-08-20) — 그날 예약한 회차 중 지금이 이용 가능
     * 시간대([회차 시작 - EARLY_ENTRY_MINUTES, 회차 종료])인 회차가 하나도 없으면 Exception400을
     * 던져 enterSession 트랜잭션 전체(세션 insert 포함)를 롤백시킨다 — 이용권 소진 차단과 같은 패턴.
     */
    @Transactional
    public void markAttended(String studentId, LocalDate serviceDate) {
        List<ReservationRespDTO.ReservationItemDTO> reservations =
                repository.findActiveReservationsByStudentAndDate(studentId, serviceDate);
        if (reservations.isEmpty()) {
            throw new Exception400("오늘 예약 내역이 없습니다. 예약 후 이용해주세요.");
        }
        boolean alreadyAttended = reservations.stream()
                .anyMatch(r -> "ATTENDED".equals(r.getStatus()));
        if (alreadyAttended) {
            return;
        }

        LocalDateTime now = KstClock.now();
        boolean withinAnyWindow = reservations.stream().anyMatch(r -> {
            LocalDateTime entryOpensAt = r.getStartsAt().minusMinutes(EARLY_ENTRY_MINUTES);
            return !now.isBefore(entryOpensAt) && !now.isAfter(r.getEndsAt());
        });
        if (!withinAnyWindow) {
            ReservationRespDTO.ReservationItemDTO first = reservations.get(0);
            throw new Exception400(first.getSeq() + "회차는 "
                    + first.getStartsAt().toLocalTime() + "~" + first.getEndsAt().toLocalTime()
                    + "에 이용 가능합니다. 이용 시간에 맞춰 다시 입실해주세요.");
        }

        for (ReservationRespDTO.ReservationItemDTO r : reservations) {
            int updated = repository.transitionStatus(r.getReservationId(), "RESERVED", "ATTENDED");
            if (updated > 0) {
                repository.insertLog(r.getReservationId(), "RESERVED", "ATTENDED", studentId, "STUDENT", null);
            }
        }
    }

    /** 그 학생이 오늘 출석 확정(ATTENDED)한 회차 수 — 책 추천 총량(회차 수 × 2권) 계산에 쓴다(2026-08-28) */
    public int countAttendedSlotsToday(String studentId) {
        return repository.countAttendedSlotsByStudentAndDate(studentId, KstClock.today());
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
        return reserveOne(student, slotInstanceId, studentId, "STUDENT");
    }

    /**
     * 센터 직원 대리 예약(2026-08-19) — 예약방법 표시("직접 예약"/"센터 예약")가 생성 로그의
     * changed_by_role을 그대로 쓰기 때문에, 학생용 reserve()를 그대로 부르면 대리 등록도
     * STUDENT로 기록돼 화면에 "직접 예약"으로 잘못 뜬다. adminUserId를 changed_by로 남겨
     * 누가 대신 등록했는지도 함께 추적한다.
     */
    @Transactional
    public ReservationRespDTO.ReservationItemDTO reserveByAdmin(String studentId, Long slotInstanceId, String adminUserId) {
        if (slotInstanceId == null) {
            throw new Exception400("예약할 회차를 선택해주세요.");
        }
        Student student = requireStudent(studentId);
        return reserveOne(student, slotInstanceId, adminUserId, "ADMIN");
    }

    @Transactional
    public void cancel(String studentId, Long reservationId, String reason) {
        cancelInternal(studentId, reservationId, reason, studentId, "STUDENT");
    }

    /** 센터 직원 대리 취소 — 생성 로그와 마찬가지로 changed_by_role을 ADMIN으로 남긴다 */
    @Transactional
    public void cancelByAdmin(String studentId, Long reservationId, String reason, String adminUserId) {
        cancelInternal(studentId, reservationId, reason, adminUserId, "ADMIN");
    }

    private void cancelInternal(String studentId, Long reservationId, String reason, String changedBy, String changedByRole) {
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
        repository.insertLog(reservationId, "RESERVED", "CANCELED", changedBy, changedByRole, reason);

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

        // 그 달 예약 상한(2026-08-28)을 미리보기에도 반영한다. 이 배치의 여러 주차가 같은 달에
        // 몰릴 수 있어, 달마다 상한과 (DB상 기존 예약 + 이 배치에서 앞서 OPEN으로 잡힌 주차)를
        // 누적 추적한다 — 상한을 넘기는 순간부터 남은 주차는 MONTH_FULL/NO_PASS로 표시한다.
        Map<YearMonth, Integer> capByMonth = new HashMap<>();
        Map<YearMonth, Integer> usedByMonth = new HashMap<>();

        List<ReservationRespDTO.BatchPreviewItemDTO> result = new ArrayList<>();
        for (LocalDate date : targetDates) {
            ReservationRespDTO.SlotOptionDTO slot = byDate.get(date);
            ReservationRespDTO.BatchPreviewItemDTO item = new ReservationRespDTO.BatchPreviewItemDTO();
            item.setServiceDate(date);
            item.setSeq(seq);

            YearMonth ym = YearMonth.from(date);
            int cap = capByMonth.computeIfAbsent(ym,
                    m -> passService.monthlyCapacity(studentId, "BOOK", m.atDay(1)));
            int used = usedByMonth.computeIfAbsent(ym,
                    m -> repository.countReservationsInMonth(studentId, m.atDay(1), m.atEndOfMonth()));

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
                } else if (repository.countOtherReservedOnDate(studentId, date, slot.getSlotInstanceId()) >= MAX_SLOTS_PER_DAY) {
                    // 하루 4회차 상한(2026-08-28) — 이 날짜에 이미 4회차를 예약해뒀으면 이 요일·회차
                    // 조합으로는 이 주차를 신청할 수 없다(reserveOne이 실제 확정 시점에도 다시 막는다)
                    item.setTargetStatus("DAY_CONFLICT");
                } else if (used >= cap) {
                    item.setTargetStatus(cap == 0 ? "NO_PASS" : "MONTH_FULL");
                } else {
                    item.setTargetStatus("OPEN");
                    usedByMonth.put(ym, used + 1);
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
            reserved.add(reserveOne(student, slotInstanceId, studentId, "STUDENT"));
        }

        log.info("[예약] 4주 일괄 확정 — studentId={}, slotInstanceIds={}", studentId, ordered);
        return reserved;
    }

    // ── 내부 ─────────────────────────────────────────────────────────────

    /**
     * 하루에 예약할 수 있는 최대 회차 수(2026-08-28, 기존 1 → 4). 월 이용권이 4회라 하루에 4회차까지
     * 몰아서 예약할 수 있다. 입실은 1일 1회지만 첫 입실로 그날 예약한 회차가 모두 출석 처리되고,
     * 이용권도 그날 회차 수만큼 차감된다.
     */
    private static final int MAX_SLOTS_PER_DAY = 4;

    private ReservationRespDTO.ReservationItemDTO reserveOne(Student student, Long slotInstanceId, String changedBy, String changedByRole) {
        // 하루 2회차 상한의 동시성 방어(2026-08-28) — (student_id, service_date) 유니크 인덱스를 없앤
        // 대신 이 학생 행을 UPDLOCK으로 잡아, 서로 다른 슬롯으로 거의 동시에 들어오는 같은 학생의
        // 요청을 DB가 한 건씩 직렬 처리하게 만든다(아래 countOtherReservedOnDate 체크가 유효해진다).
        repository.lockStudentForReservation(student.getStudentId());

        ReservationRespDTO.SlotOptionDTO slot = repository.findSlotOptionById(slotInstanceId);
        if (slot == null) {
            throw new Exception404("존재하지 않는 회차입니다.");
        }

        // 이미 끝난 회차는 예약해도 입실할 수 없다(markAttended가 회차 시간대 밖을 막는다). 목록에서
        // CLOSED로 내려주지만 목록만 고치면 API를 직접 호출해 우회할 수 있어 여기서도 막는다.
        // 직원 대리 예약도 같은 규칙을 탄다 — 지난 회차로 잡아주면 그 학생은 입실을 못 한다.
        if (!slot.getEndsAt().isAfter(KstClock.now())) {
            throw new Exception400(slotLabel(slot.getServiceDate(), slot.getSeq()) + "는 이미 종료된 회차입니다.");
        }

        // 그 달 예약 상한 하드체크(2026-08-28) — "그 달에 이미 잡아둔 비취소 예약 건수 ≥ 그 달
        // 이용권 total_count 합"이면 막는다. 슬롯의 service_date가 속한 달을 기준으로 본다(오늘이
        // 아니라) — 4주 일괄(reserveOne을 날짜별로 반복)이 월 경계를 넘어도 각 슬롯이 자기 달의
        // 상한을 탄다. 차감/홀드는 하지 않는다(예약 취소가 잦아 홀드 방식은 되돌림 비용이 큼) —
        // 실제 이용권 차감은 입실 시점(MonitorService.enterSession)에 그날 회차 수만큼 일어난다.
        // 그 달 이용권이 아직 없으면 상한 0 → 결제 전까지 그 달 예약 불가.
        // 위 lockStudentForReservation이 같은 학생의 동시 예약 요청을 직렬화하므로, 이 SELECT 뒤
        // INSERT까지 다른 요청이 끼어들어 상한을 넘기지 못한다.
        int monthlyCap = passService.monthlyCapacity(student.getStudentId(), "BOOK", slot.getServiceDate());
        int reservedInMonth = repository.countReservationsInMonth(student.getStudentId(),
                slot.getServiceDate().withDayOfMonth(1),
                slot.getServiceDate().withDayOfMonth(1).plusMonths(1).minusDays(1));
        if (reservedInMonth >= monthlyCap) {
            if (monthlyCap == 0) {
                throw new Exception400(slotLabel(slot.getServiceDate(), slot.getSeq())
                        + " — 해당 월 이용권이 없습니다. 결제 후 예약해주세요.");
            }
            throw new Exception400(slotLabel(slot.getServiceDate(), slot.getSeq())
                    + " — 그 달 예약 가능 횟수(" + monthlyCap + "회)를 모두 채우셨습니다.");
        }

        // 하루 2회차 상한 정책(2026-08-28, 기존 1 → 2) — 4주 일괄(reserveOne을 날짜별로 반복 호출)로
        // 들어와도 그대로 적용된다. 같은 슬롯 재예약 시도도 여기서 걸린다(2026-08-20 — 유니크 인덱스는
        // status='RESERVED'만 보므로 ATTENDED인 슬롯 재예약까지는 못 막았었다).
        // 위 lockStudentForReservation이 같은 학생의 동시 요청을 직렬화하므로, 이 평문 SELECT 뒤에
        // INSERT까지 다른 요청이 끼어들지 못한다 — (student_id, service_date) 유니크 인덱스를 없앤
        // 자리를 이 학생 행 락이 대신 지킨다.
        if (repository.countOtherReservedOnDate(student.getStudentId(), slot.getServiceDate(), slotInstanceId) >= MAX_SLOTS_PER_DAY) {
            throw new Exception400(slotLabel(slot.getServiceDate(), slot.getSeq())
                    + " — 이 날짜엔 이미 " + MAX_SLOTS_PER_DAY + "회차를 예약하셨습니다. 하루에 "
                    + MAX_SLOTS_PER_DAY + "회차까지만 예약할 수 있습니다.");
        }

        int updated = repository.incrementReservedCount(slotInstanceId);
        if (updated == 0) {
            throw new Exception400(slotLabel(slot.getServiceDate(), slot.getSeq()) + "는 마감되었습니다.");
        }

        ReservationReqDTO.InsertReservationDTO command = new ReservationReqDTO.InsertReservationDTO();
        command.setSlotInstanceId(slotInstanceId);
        command.setStudentId(student.getStudentId());
        // 날짜는 요청이 아니라 슬롯에서 가져온다 — 이 값이 하루 한 회차 제약의 기준이므로
        // 호출자가 임의로 넣을 수 있게 두면 제약 자체를 우회할 수 있다.
        command.setServiceDate(slot.getServiceDate());
        command.setChannel(changedByRole);
        try {
            repository.insertReservation(command);
        } catch (DuplicateKeyException e) {
            // 남은 유니크 인덱스는 UX_reservation_slot_student(슬롯 기준, status='RESERVED')뿐이다 —
            // 같은 회차를 두 번 예약하려 한 경우다. 하루 2회차 상한은 위 학생 행 락 + 카운트가 막으므로
            // 여기로는 안 온다.
            throw new Exception400(slotLabel(slot.getServiceDate(), slot.getSeq())
                    + " — 이미 예약한 회차입니다.");
        }

        repository.insertLog(command.getReservationId(), null, "RESERVED", changedBy, changedByRole, null);
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
        dto.setContact(student.getBillingPhone());
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
