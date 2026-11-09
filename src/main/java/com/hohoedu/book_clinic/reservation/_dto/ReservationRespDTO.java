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
        /** 이 슬롯에 조회 주체가 이미 예약해뒀는지(RESERVED/ATTENDED/PAY_WAIT) */
        private Boolean reservedByMe;
    }

    /**
     * 4주 일괄 신청 미리보기 1건.
     * targetStatus: OPEN(신청 가능) / ALREADY_RESERVED(이미 내 예약 있음) /
     *               FULL(마감) / CLOSED(휴무·마감된 회차) / NOT_OPEN(아직 슬롯이 생성되지 않음) /
     *               DAY_CONFLICT(이 날짜에 이미 하루 상한(4회차)만큼 예약함) /
     *               MONTH_FULL(이용권 잔여를 이미 다 씀 — 예약 시 차감이라 잔여 = 예약 가능 횟수) /
     *               NO_PASS(그 날짜를 덮는 이용권이 없음 — 결제 후 예약 가능)
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
        /** 여러 학생을 한 번에 훑는 조회(결제대기 정리 배치)에서만 채워진다 */
        private String studentId;
        private LocalDate serviceDate;
        private Integer seq;
        private LocalDateTime startsAt;
        private LocalDateTime endsAt;
        /** RESERVED / CANCELED / ATTENDED / NOSHOW / PAY_WAIT(결제대기 — 자리만 잡아둠, 입실 불가, 2026-10-27) */
        private String status;
        private LocalDateTime reservedAt;
        /**
         * 학생 채널에서 지금 취소·변경할 수 있는지 (2026-09-14, 회차 시작 24시간 전까지).
         * 서버가 취소 시점에 다시 검사하므로 이 값은 화면 표시용이다 — 목록을 띄워둔 채
         * 마감을 넘겨도 취소 자체는 서버에서 막힌다. 목록 조회에서만 채워진다.
         */
        private Boolean cancelable;
        /**
         * 실제 입실·퇴실 시각 "HH:mm" (2026-10-14, 앱 출석체크 화면). 미입실/미퇴실이면 null.
         * 예정 시각(startsAt/endsAt)과 달리 실제 이용 기록이다. 월별 이용 기록 조회에서만 채워진다.
         */
        private String checkIn;
        private String checkOut;
    }

    /**
     * 대리 예약 화면(관리자)의 날짜별 목록 1행 (2026-08-19, 전체 필드 확장).
     * status: RESERVED/CANCELED/ATTENDED/NOSHOW/PAY_WAIT. channel: 예약 생성 로그의 changed_by_role
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

    // ── 매주 자동 예약 (2026-10-27) ──────────────────────────────────────

    /**
     * 자동 예약 패턴 1건 — "매주 ○요일 ○회차".
     * state: ON(정상) / OFF(꺼짐) / NO_PASS(켜져 있지만 이용권이 없어 멈춤 — 구매하면 자동으로 이어진다)
     */
    @Data
    public static class AutoPatternDTO {
        private Integer autoId;
        private String studentId;
        /** ISO 요일 1=월 ~ 7=일 */
        private Integer dayOfWeek;
        private Integer seq;
        private Boolean enabled;
        /** 여기까지는 자동 예약을 시도했다 — 다음 시도는 이 다음 날부터 */
        private LocalDate filledUntil;
        /** 이용권이 없어 멈춘 날. null이면 정상 */
        private LocalDate noPassAt;
        private String state;
    }

    /**
     * 자동 예약 1회 시도 결과.
     * outcome: RESERVED(예약됨) / HELD(결제대기로 자리만 잡음) / ALREADY(이미 내 예약) /
     *          NO_PASS(이용권 없음) / FAILED(마감·하루 상한 등 — message에 사유)
     */
    @Data
    public static class AutoAttemptDTO {
        private LocalDate serviceDate;
        private Integer seq;
        private String outcome;
        private Long reservationId;
        private String message;

        public static AutoAttemptDTO of(String outcome, Long reservationId, String message) {
            AutoAttemptDTO dto = new AutoAttemptDTO();
            dto.setOutcome(outcome);
            dto.setReservationId(reservationId);
            dto.setMessage(message);
            return dto;
        }
    }

    /** 10시 일괄 안내 대기열 1줄 */
    @Data
    public static class AutoNoticeDTO {
        private Integer noticeId;
        private String studentId;
        private String message;
    }

}
