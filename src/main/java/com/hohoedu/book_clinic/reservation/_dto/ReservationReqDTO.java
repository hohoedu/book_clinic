package com.hohoedu.book_clinic.reservation._dto;

import java.time.LocalDate;

import lombok.Data;

/** 클리닉 예약 관련 요청 DTO 모음 (2026-07-23) */
public class ReservationReqDTO {

    /** 예약 등록 요청 */
    @Data
    public static class RegisterReqDTO {
        private String studentId;
        private LocalDate reservationDate;
        private String timeSlot;
    }

    /** 예약 삭제 요청 */
    @Data
    public static class DeleteReqDTO {
        private Integer reservationId;
    }

}
