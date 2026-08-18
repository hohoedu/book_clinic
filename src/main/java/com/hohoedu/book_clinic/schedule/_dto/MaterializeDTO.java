package com.hohoedu.book_clinic.schedule._dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * materialize() 엔진 전용 DTO 모음 (2026-08-18).
 *
 * 화면 응답용인 ScheduleRespDTO와 분리한 이유는 용도가 정반대여서다. 저쪽은 관리자가 보는 "규칙"의
 * 표현이고, 이쪽은 규칙 + 예외를 날짜에 적용해 slot_instance로 찍어내기 위한 계산 입력·출력이다.
 */
public class MaterializeDTO {

    /** 요일 규칙 1버전 (erp_bookstore_schedule) */
    @Data
    public static class RuleDTO {
        private Integer dayOfWeek;
        private LocalDate effectiveFrom;
        private Boolean isOpen;
        private LocalTime openTime;
        private LocalTime closeTime;
        private Integer slotMinutes;
        private Integer breakMinutes;
        private Integer defaultCapacity;
    }

    /** 회차 템플릿 1건 (erp_bookstore_schedule_slot) — 어느 버전 소속인지까지 들고 온다 */
    @Data
    public static class TemplateSlotDTO {
        private Integer dayOfWeek;
        private LocalDate effectiveFrom;
        private Integer seq;
        private LocalTime startTime;
        private LocalTime endTime;
        private Integer capacity;
    }

    /** 기간 예외 1건 (erp_bookstore_schedule_exception) */
    @Data
    public static class ExceptionDTO {
        private Integer exceptionId;
        private LocalDate startDate;
        private LocalDate endDate;
        /** CLOSED / TIME_CHANGE / SLOT_CHANGE */
        private String exceptionType;
        private LocalTime openTime;
        private LocalTime closeTime;
    }

    /** 회차별 예외 오버라이드 (erp_bookstore_schedule_exception_slot) */
    @Data
    public static class ExceptionSlotDTO {
        private Integer exceptionId;
        private Integer seq;
        private Boolean isClosed;
        /** null이면 템플릿 인원 유지 — 0(정원 0명)과 의미가 다르다 */
        private Integer capacity;
        /** TIME_CHANGE에서 관리자가 확정한 회차 시각. SLOT_CHANGE면 null */
        private LocalTime startTime;
        private LocalTime endTime;
    }

    /** 특정 날짜에 찍어낼 슬롯 1건 — MERGE의 입력값 */
    @Data
    public static class InstanceDTO {
        private Integer seq;
        private LocalDateTime startsAt;
        private LocalDateTime endsAt;
        private Integer capacity;
        /** OPEN / CLOSED */
        private String status;
        /** TEMPLATE / EXCEPTION — 이 슬롯이 어디서 나왔는지 역추적용 */
        private String sourceType;
    }

    /**
     * 실행 결과.
     *
     * blockedDates는 "예약이 이미 걸려 있어 손대지 않고 건너뛴 날짜"다. 엔진이 예외를 던지지 않고
     * 목록으로 돌려주는 이유는, 호출자마다 대응이 다르기 때문이다 — 요일 저장은 애초에 예약이
     * 있으면 거부하고(결정 4), 일일 배치는 로그만 남기고 나머지 날짜를 계속 처리해야 한다.
     */
    @Data
    @AllArgsConstructor
    public static class ResultDTO {
        private int dateCount;
        private int slotCount;
        private List<LocalDate> blockedDates;
    }

}
