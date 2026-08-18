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

    /**
     * 등록된 예외 일정 1건 — 화면 오른쪽 "등록된 예외 일정" 목록용.
     *
     * summary는 서버가 만들어 내려준다. "3, 4회차 마감"처럼 회차 목록을 문장으로 합치는 규칙을
     * 화면마다 다시 구현하면 관리자 화면과 나중에 붙을 학생 안내 문구가 어긋나기 쉽다.
     */
    @Data
    public static class ExceptionDTO {
        private Integer exceptionId;
        private LocalDate startDate;
        private LocalDate endDate;
        /** CLOSED / TIME_CHANGE / SLOT_CHANGE */
        private String exceptionType;
        /** 화면 뱃지에 그대로 쓰는 한글 이름 (휴무 / 운영시간 변경 / 운영회차 변경) */
        private String exceptionTypeLabel;
        private String reason;
        @JsonFormat(pattern = "HH:mm")
        private LocalTime openTime;
        @JsonFormat(pattern = "HH:mm")
        private LocalTime closeTime;
        /** 회차 조정 요약 — SLOT_CHANGE가 아니면 null */
        private String slotSummary;
        /**
         * 회차 조정 원본. 요약(slotSummary)은 사람이 읽는 문장이라 화면이 "그날 3회차가 마감인가"를
         * 판단하는 데는 쓸 수 없어서, 계산에 쓸 값을 따로 내려준다.
         */
        private List<MaterializeDTO.ExceptionSlotDTO> slotOverrides;
        /** 이미 지나간 예외인지 — 목록에서 흐리게 표시하고 삭제를 막기 위한 값 */
        private Boolean past;
        /**
         * PAST / CURRENT / UPCOMING — 버전 카드(VersionSummaryDTO)와 같은 3단계.
         * 두 종류를 한 타임라인에 섞어 보여주므로 상태 구분이 서로 어긋나면 안 된다.
         */
        private String status;
        /** 지난 / 진행중 / 예정 */
        private String statusLabel;
    }

    /**
     * 스케줄 변경 예약 이력 1건 — 오른쪽 패널의 카드 하나.
     *
     * 이 DTO는 테이블에 그대로 있는 행이 아니라 계산 결과다. erp_bookstore_schedule은 "언제부터"만
     * 갖고 있어서, 종료일은 다음 버전 시작일에서 하루를 뺀 값으로 만들어진다. 종료일을 컬럼으로
     * 들고 있으면 새 버전을 저장할 때마다 직전 버전을 같이 고쳐야 하고 두 값이 어긋날 수 있다.
     */
    @Data
    public static class VersionSummaryDTO {
        private LocalDate effectiveFrom;
        /** 다음 버전 시작일 - 1일. 마지막 버전이면 null(무기한) */
        private LocalDate endDate;
        /** PAST / CURRENT / UPCOMING */
        private String status;
        /** 이전 / 운영중 / 예정 */
        private String statusLabel;
        /** 그 시점부터 실제로 운영되는 요일들 (ISO 1=월). 이 버전에서 바뀐 요일만이 아니라 주간 전체 구성 */
        private List<Integer> openDays;
        /** "월, 화, 목, 금, 토" — 화면이 매번 같은 규칙을 다시 짜지 않도록 서버가 만들어 준다 */
        private String openDayLabel;
        /** 예정 버전만 지울 수 있다 — 이미 지나갔거나 운영 중인 버전은 기록이다 */
        private Boolean deletable;
    }

    /** 버전 목록 계산용 원본 행 (요일 1개의 버전 1건) */
    @Data
    public static class VersionDayDTO {
        private Integer dayOfWeek;
        private LocalDate effectiveFrom;
        private Boolean isOpen;
    }

}
