package com.hohoedu.book_clinic.schedule._dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/** 운영 스케줄(요일별 규칙) 응답 DTO 모음 (2026-08-14) */
public class ScheduleRespDTO {

    /** 운영 스케줄 화면 전체 — 월~일 7개를 항상 채워서 내려준다 */
    @Data
    public static class WeekDTO {
        private String centerCode;
        /** 어느 날짜 기준으로 유효 버전을 골랐는지 (기본값 오늘 KST) */
        private LocalDate baseDate;
        private List<DayDTO> days;
    }

    /**
     * 요일 1개의 유효 버전.
     *
     * 아직 한 번도 저장한 적 없는 요일이면 effectiveFrom이 null이고 나머지는 기본값이다 —
     * 화면이 "데이터 없음" 분기를 따로 만들지 않아도 되도록 7개 요일을 빠짐없이 채운다.
     */
    @Data
    public static class DayDTO {
        /** ISO 8601 · 1=월 ~ 7=일 */
        private Integer dayOfWeek;
        /** 이 버전의 적용 시작일. null이면 저장된 버전이 없다는 뜻 */
        private LocalDate effectiveFrom;
        private Boolean isOpen;
        @JsonFormat(pattern = "HH:mm")
        private LocalTime openTime;
        @JsonFormat(pattern = "HH:mm")
        private LocalTime closeTime;
        private Integer slotMinutes;
        private Integer breakMinutes;
        private Integer defaultCapacity;
        private List<SlotDTO> slots;
        /**
         * baseDate 이후에 적용될 예정인 버전들의 적용 시작일 (오름차순).
         * 화면 상단에 "n월 n일부터 변경 예정" 안내를 띄우기 위한 값.
         */
        private List<LocalDate> upcomingVersions;
    }

    /** 회차 템플릿 1건 */
    @Data
    public static class SlotDTO {
        private Integer slotTemplateId;
        /** 조회 쿼리에서 요일별로 묶기 위해 함께 내려받는 값 (응답에도 그대로 포함) */
        private Integer dayOfWeek;
        private Integer seq;
        @JsonFormat(pattern = "HH:mm")
        private LocalTime startTime;
        @JsonFormat(pattern = "HH:mm")
        private LocalTime endTime;
        private Integer capacity;
    }

    /** 미래 버전 목록 조회용 (요일 → 적용 시작일) */
    @Data
    public static class VersionDTO {
        private Integer dayOfWeek;
        private LocalDate effectiveFrom;
    }

}
