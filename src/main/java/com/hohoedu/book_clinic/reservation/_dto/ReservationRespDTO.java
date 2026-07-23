package com.hohoedu.book_clinic.reservation._dto;

import lombok.Data;

/** 클리닉 예약 관련 응답 DTO 모음 (2026-07-23) */
public class ReservationRespDTO {

    /** 예약 목록 1건 — 예약 등록 화면의 우측 목록 테이블용 */
    @Data
    public static class ReservationRowDTO {
        private Integer reservationId;
        private String studentId;
        private String studentName;
        private String school;
        private String gradeKey;
        private String timeSlot;
    }

    /** 학생 검색 결과 1건 — 예약 등록 화면의 좌측 학생 검색용 */
    @Data
    public static class StudentSearchDTO {
        private String studentId;
        private String studentName;
        private String school;
        private String gradeKey;
        private String appId;
    }

}
