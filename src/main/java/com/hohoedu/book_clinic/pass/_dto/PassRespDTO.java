package com.hohoedu.book_clinic.pass._dto;

import java.time.LocalDate;
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
        private LocalDate validFrom;
        private LocalDate validUntil;
        private int totalCount;
        private int remainCount;
        private LocalDateTime grantedAt;
        private LocalDateTime revokedAt;
    }

    /**
     * 어떤 날짜가 속한 이용 주기(2026-09-07 자동결제 전환).
     *
     * 자동결제는 결제일 기준 1개월이라 "그 달"이라는 단위가 더 이상 통하지 않는다. 예약 상한처럼
     * "이 기간에 몇 번 쓸 수 있나"를 묻는 곳은 달력 월 대신 이 주기를 기준으로 삼는다.
     * 서당 일괄청구분은 이용권이 1일~말일로 발급되므로 이 주기가 곧 달력 월이 되어 같은 코드로 처리된다.
     *
     * 겹치는 이용권이 여러 장이면(예: 서당분 + 앱 결제분) capacity는 합계이고 기간은 그 합집합의
     * 시작~끝이다. 그 날짜를 덮는 이용권이 하나도 없으면 capacity=0이고 기간은 null이다.
     */
    @Data
    public static class CycleDTO {
        private LocalDate cycleFrom;
        private LocalDate cycleUntil;
        /** 그 주기에 원래 부여된 총 횟수 합 — 화면 표시용 */
        private int capacity;
        /** 그 주기에 아직 남은 횟수 합 — 예약 가능 여부는 이 값으로 판정한다(2026-09-14 예약 시 차감) */
        private int remaining;
    }

    /** 차감 이력 한 줄 — 예약 취소 시 되돌릴 대상을 집어내는 데만 쓴다 */
    @Data
    public static class UseDTO {
        private int useId;
        private int passId;
        private String studentId;
        private Long reservationId;
        private LocalDate usedDate;
    }

    /** 잔여 횟수 조회 응답 — 학생 화면 상단에 "남은 횟수" 표시용 */
    @Data
    public static class RemainDTO {
        private final String serviceCode;
        private final int remainCount;
    }
}
