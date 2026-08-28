package com.hohoedu.book_clinic.reservation._dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

/** 예약(erp_bookstore_reservation) 응답 DTO 모음 (2026-08-18, old에서 이관) */
public class ReservationRespDTO {

    /** 예약 가능한 슬롯 1건 */
    @Data
    public static class SlotOptionDTO {
        private Long slotInstanceId;
        private LocalDate serviceDate;
        private Integer seq;
        private LocalDateTime startsAt;
        private LocalDateTime endsAt;
        private Integer capacity;
        private Integer reservedCount;
        /** OPEN / CLOSED */
        private String status;
        /** 이 슬롯에 조회 주체가 이미 RESERVED 상태로 예약해뒀는지 */
        private Boolean reservedByMe;
    }

    /**
     * 4주 일괄 신청 미리보기 1건.
     * targetStatus: OPEN(신청 가능) / ALREADY_RESERVED(이미 내 예약 있음) /
     *               FULL(마감) / CLOSED(휴무·마감된 회차) / NOT_OPEN(아직 슬롯이 생성되지 않음) /
     *               DAY_CONFLICT(이 날짜에 이미 하루 상한(4회차)만큼 예약함) /
     *               MONTH_FULL(그 달 예약 가능 횟수를 이미 채움 — 이용권 total_count 기준) /
     *               NO_PASS(그 달 이용권이 아직 없음 — 결제 후 예약 가능)
     */
    @Data
    public static class BatchPreviewItemDTO {
        private Long slotInstanceId;
        private LocalDate serviceDate;
        private Integer seq;
        private LocalDateTime startsAt;
        private LocalDateTime endsAt;
        private String targetStatus;
    }

    /** 예약 1건 (등록 결과 / 내 예약 목록 공용) */
    @Data
    public static class ReservationItemDTO {
        private Long reservationId;
        private Long slotInstanceId;
        private LocalDate serviceDate;
        private Integer seq;
        private LocalDateTime startsAt;
        private LocalDateTime endsAt;
        private String status;
        private LocalDateTime reservedAt;
    }

    /**
     * 대리 예약 화면(관리자)의 날짜별 목록 1행 (2026-08-19, 전체 필드 확장).
     * status: RESERVED/CANCELED/ATTENDED/NOSHOW. channel: 예약 생성 로그의 changed_by_role
     * (STUDENT/PARENT → 직접 예약, ADMIN → 센터 예약)을 그대로 내려주고 화면에서 라벨링한다.
     */
    @Data
    public static class AdminReservationRowDTO {
        private Long reservationId;
        private String studentId;
        private String studentName;
        private String school;
        private String gradeKey;
        private String contact;
        private Integer seq;
        private LocalDateTime startsAt;
        private LocalDateTime endsAt;
        private String status;
        private String channel;
        private String reason;
        private Integer monthlyAttendCount;
    }

    /** 학생 검색 결과 1건 — 대리 예약 화면 좌측 학생 검색용 */
    @Data
    public static class StudentSearchDTO {
        private String studentId;
        private String studentName;
        private String school;
        private String gradeKey;
        private String appId;
        private String contact;
    }

}
