package com.hohoedu.book_clinic.pass._dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 이용권 응답/조회 DTO.
 */
public class PassRespDTO {

    /** 이용권 한 장 */
    @Data
    public static class PassDTO {
        private int passId;
        private String studentId;
        private String centerCode;
        private int productId;
        private String serviceCode;
        /** PG / SEODANG / FREE */
        private String source;
        /** PG=payment.order_no, SEODANG=all_pass 청구 bill_id */
        private String refNo;
        private String billingYm;
        private int totalCount;
        private int remainCount;
        private LocalDateTime grantedAt;
        private LocalDateTime revokedAt;
    }

    /** 잔여 횟수 조회 응답 — 학생 화면 상단에 "남은 횟수" 표시용 */
    @Data
    public static class RemainDTO {
        private final String serviceCode;
        private final int remainCount;
    }
}
