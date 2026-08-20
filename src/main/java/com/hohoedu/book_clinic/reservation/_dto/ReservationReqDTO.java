package com.hohoedu.book_clinic.reservation._dto;

import java.time.LocalDate;
import java.util.List;

import lombok.Data;

/** 예약(erp_bookstore_reservation) 요청 DTO 모음 (2026-08-18, old에서 이관) */
public class ReservationReqDTO {

    // ── 학생 앱 ──────────────────────────────────────────────────────────

    @Data
    public static class ReserveReqDTO {
        private Long slotInstanceId;
    }

    @Data
    public static class CancelReqDTO {
        private Long reservationId;
        private String reason;
    }

    /** batch-preview에서 확인한 슬롯 id만 골라 보낸다 — 이미 예약된 주차는 클라이언트가 미리 제외한다 */
    @Data
    public static class BatchReserveReqDTO {
        private List<Long> slotInstanceIds;
    }

    // ── 센터 직원 대리 예약 ──────────────────────────────────────────────
    // 대상 학생을 세션이 아니라 요청 본문으로 직접 지정한다는 점만 학생용과 다르다.
    // 뒤에서 부르는 ReservationService 로직은 완전히 동일하다(직원이라고 정원 우선권을 갖지 않는다).

    @Data
    public static class AdminReserveReqDTO {
        private String studentId;
        private Long slotInstanceId;
    }

    @Data
    public static class AdminCancelReqDTO {
        private String studentId;
        private Long reservationId;
        private String reason;
    }

    // ── 내부 커맨드 ──────────────────────────────────────────────────────

    /** 예약 INSERT 전용 — reservationId는 MyBatis useGeneratedKeys로 INSERT 이후 채워지는 출력 필드다 */
    @Data
    public static class InsertReservationDTO {
        private Long slotInstanceId;
        private String studentId;
        /** 슬롯에서 복사해 넣는다 — UX_reservation_student_date가 이 컬럼으로 하루 한 회차를 강제한다 */
        private LocalDate serviceDate;
        /** STUDENT/PARENT/ADMIN/SYSTEM — 예약 현황 화면의 "직접 예약"/"센터 예약" 표시에 그대로 쓰인다 */
        private String channel;
        private Long reservationId;
    }

}
