package com.hohoedu.book_clinic.schedule._dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 운영 스케줄(요일별 규칙) 요청 DTO 모음 (2026-08-14) */
public class ScheduleReqDTO {

    /**
     * 요일 스케줄 저장 요청.
     *
     * 화면의 저장 버튼 하나가 월~일 전체를 한 번에 넘긴다. 요일별로 따로 저장하는 API를 두지
     * 않은 이유는 "적용 시작일"이 화면 전체에 하나뿐이기 때문 — 요일마다 다른 적용일을 갖게 하면
     * 사용자가 지금 보고 있는 화면이 어느 시점의 스케줄인지 설명할 수 없게 된다.
     */
    @Data
    public static class SaveWeekReqDTO {
        /** 즉시적용 체크 시 서버가 오늘(KST)로 채운다. 그때는 effectiveFrom을 보내지 않아도 된다 */
        private Boolean applyNow;
        private LocalDate effectiveFrom;

        @Valid
        @NotEmpty(message = "저장할 요일이 없습니다.")
        private List<DayReqDTO> days;
    }

    /** 요일 1개의 운영 규칙 + 회차 목록 */
    @Data
    public static class DayReqDTO {
        @NotNull(message = "요일이 없습니다.")
        private Integer dayOfWeek;

        @NotNull(message = "운영/휴무 여부가 없습니다.")
        private Boolean isOpen;

        @JsonFormat(pattern = "HH:mm")
        private LocalTime openTime;
        @JsonFormat(pattern = "HH:mm")
        private LocalTime closeTime;

        private Integer slotMinutes;
        private Integer breakMinutes;
        private Integer defaultCapacity;

        /** 휴무(isOpen=false)면 무시된다 */
        @Valid
        private List<SlotReqDTO> slots;
    }

    /**
     * 회차 1건. seq는 받지 않는다 — 드래그 정렬이 있는 화면이라 클라이언트가 보내는 번호를
     * 그대로 믿으면 중복·누락이 생길 수 있어, 서버가 시작시각 순으로 1부터 다시 매긴다.
     */
    @Data
    public static class SlotReqDTO {
        @NotNull(message = "회차 시작시각이 없습니다.")
        @JsonFormat(pattern = "HH:mm")
        private LocalTime startTime;

        @NotNull(message = "회차 종료시각이 없습니다.")
        @JsonFormat(pattern = "HH:mm")
        private LocalTime endTime;

        private Integer capacity;
    }

}
