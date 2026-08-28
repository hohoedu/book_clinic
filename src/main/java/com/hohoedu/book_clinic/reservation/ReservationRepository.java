package com.hohoedu.book_clinic.reservation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hohoedu.book_clinic.reservation._dto.ReservationReqDTO;
import com.hohoedu.book_clinic.reservation._dto.ReservationRespDTO;

/**
 * 예약(erp_bookstore_reservation / slot_instance) 매퍼 (2026-08-18).
 * old erp_bookstore_clinic_reservation을 완전히 대체한다 — 정원 개념이 없던 old 스키마와 달리
 * capacity/reserved_count를 다루므로 조건부 UPDATE 기반 동시성 제어가 이 매퍼의 핵심이다.
 */
@Mapper
public interface ReservationRepository {

    /** 학생이 고를 수 있는 열린(OPEN) 슬롯 목록 — 예약 화면 기본 조회 */
    List<ReservationRespDTO.SlotOptionDTO> findOpenSlots(@Param("centerCode") String centerCode,
                                                      @Param("fromDate") LocalDate fromDate,
                                                      @Param("toDate") LocalDate toDate,
                                                      @Param("studentId") String studentId);

    /** 특정 요일·회차에 해당하는 날짜들의 슬롯 — 4주 일괄 미리보기용. OPEN이 아닌 슬롯도 포함한다 */
    List<ReservationRespDTO.SlotOptionDTO> findSlotsByDates(@Param("centerCode") String centerCode,
                                                         @Param("dates") List<LocalDate> dates,
                                                         @Param("seq") Integer seq,
                                                         @Param("studentId") String studentId);

    /** id 목록으로 슬롯 조회 — 배치 확정 시 날짜 오름차순 잠금 순서를 정하기 위해 필요 */
    List<ReservationRespDTO.SlotOptionDTO> findSlotsByIds(@Param("ids") List<Long> ids);

    /** 슬롯 1건 — 예약 실패 시 안내 문구("9월 23일 목요일 2회차는 마감되었습니다")를 만들 때 쓴다 */
    ReservationRespDTO.SlotOptionDTO findSlotOptionById(@Param("slotInstanceId") Long slotInstanceId);

    /**
     * 같은 학생이 같은 날짜에 다른 회차를 이미 예약해뒀는지 — "하루 1회차만" 정책(2026-08-18) 판정용.
     * slotInstanceId로 지금 시도 중인 슬롯 자체는 제외한다(같은 슬롯 재예약 시도는 이 규칙이 아니라
     * 중복예약 유니크 인덱스가 걸러야 정확한 메시지가 나간다).
     */
    int countOtherReservedOnDate(@Param("studentId") String studentId,
                                 @Param("serviceDate") LocalDate serviceDate,
                                 @Param("slotInstanceId") Long slotInstanceId);

    /**
     * 예약 트랜잭션 시작 시 이 학생의 erp_student 행을 UPDLOCK으로 잡아 같은 학생의 동시 예약
     * 요청을 직렬화한다(2026-08-28, 하루 2회차 상한의 동시성 방어). 반환값은 쓰지 않는다.
     */
    String lockStudentForReservation(@Param("studentId") String studentId);

    /** service_date가 [monthStart, monthEnd]에 드는 이 학생의 비취소(RESERVED/ATTENDED/NOSHOW) 예약 건수 — 그 달 예약 상한 판정용 */
    int countReservationsInMonth(@Param("studentId") String studentId,
                                 @Param("monthStart") LocalDate monthStart,
                                 @Param("monthEnd") LocalDate monthEnd);

    /** 그 학생의 그날 진행 중(RESERVED/ATTENDED) 예약 전체 — 첫 입실 시 일괄 ATTENDED 전환용. 회차 시작 시각 오름차순 */
    List<ReservationRespDTO.ReservationItemDTO> findActiveReservationsByStudentAndDate(@Param("studentId") String studentId,
                                                                                       @Param("serviceDate") LocalDate serviceDate);

    /** 그 학생의 그날 출석 확정(ATTENDED) 회차 수 — 책 추천 총량(회차 수 × 2권) 계산용 */
    int countAttendedSlotsByStudentAndDate(@Param("studentId") String studentId,
                                           @Param("serviceDate") LocalDate serviceDate);

    /**
     * 조건부 증가 — "OPEN이고 정원 여유가 있으면"만 1 늘린다. 확인과 갱신을 한 문장으로 묶어
     * DB 행 락으로 동시 요청을 한 명씩 처리하게 만드는 것이 동시성 제어의 핵심이다.
     * 영향행수 0이면 마감(혹은 존재하지 않음)이라는 뜻이다.
     */
    int incrementReservedCount(@Param("slotInstanceId") Long slotInstanceId);

    /** 취소 시 정원 반납. 0 밑으로 내려가지 않도록 방어 조건을 같이 둔다 */
    int decrementReservedCount(@Param("slotInstanceId") Long slotInstanceId);

    /** 예약 INSERT — command.reservationId에 생성키가 채워져 돌아온다 */
    void insertReservation(ReservationReqDTO.InsertReservationDTO command);

    /** 상태 전환 이력 — 예약 생성이면 fromStatus는 null */
    void insertLog(@Param("reservationId") Long reservationId,
                   @Param("fromStatus") String fromStatus,
                   @Param("toStatus") String toStatus,
                   @Param("changedBy") String changedBy,
                   @Param("changedByRole") String changedByRole,
                   @Param("reason") String reason);

    /** 조건부 취소 — RESERVED 상태의 본인(대상 학생) 예약만 전환한다(이중취소·타인 예약 취소 방지) */
    int cancelReservation(@Param("reservationId") Long reservationId,
                          @Param("studentId") String studentId,
                          @Param("reason") String reason);

    /** 취소 전 정원 반납 대상 슬롯을 알아내기 위한 조회. 대상 학생 소유가 아니면 null */
    Long findSlotInstanceIdByReservationId(@Param("reservationId") Long reservationId,
                                           @Param("studentId") String studentId);

    ReservationRespDTO.ReservationItemDTO findReservationById(@Param("reservationId") Long reservationId);

    /** 학생 본인의 RESERVED 예약 목록 (기본: 오늘 이후) */
    List<ReservationRespDTO.ReservationItemDTO> findMyReservations(@Param("studentId") String studentId,
                                                                @Param("fromDate") LocalDate fromDate);

    /** 대리 예약 화면(관리자) — 특정 센터·날짜의 예약 목록(모든 상태 포함) */
    List<ReservationRespDTO.AdminReservationRowDTO> findReservationsByDate(@Param("centerCode") String centerCode,
                                                                       @Param("date") LocalDate date);

    /** 특정 센터·날짜의 회차별 정원 요약(회차 카드용) — 학생 구분 없이 그날의 슬롯을 전부 가져온다 */
    List<ReservationRespDTO.SlotOptionDTO> findSlotsByDate(@Param("centerCode") String centerCode,
                                                        @Param("date") LocalDate date);

    // ── 출결 전환 (ATTENDED/NOSHOW, 2026-08-18) ─────────────────────────

    /** 그 학생의 그날 RESERVED 예약(+회차 시작/종료 시각) — 입실 시 ATTENDED 전환 대상 + 시간대 검증용. 없으면 null */
    ReservationRespDTO.ReservationItemDTO findReservedSlotByStudentAndDate(@Param("studentId") String studentId,
                                                                            @Param("serviceDate") LocalDate serviceDate);

    /**
     * 조건부 상태 전환 — fromStatus일 때만 toStatus로 바꾼다. cancelReservation과 같은 패턴을
     * ATTENDED/NOSHOW에도 재사용한다(이미 전환된 건 다시 안 건드림 = 여러 번 불려도 안전).
     */
    int transitionStatus(@Param("reservationId") Long reservationId,
                         @Param("fromStatus") String fromStatus,
                         @Param("toStatus") String toStatus);

    /** 슬롯이 끝났는데(ends_at < asOf) 아직 RESERVED로 남아있는 예약 id들 — 노쇼 배치 대상 */
    List<Long> findNoShowCandidates(@Param("asOf") LocalDateTime asOf);

}
