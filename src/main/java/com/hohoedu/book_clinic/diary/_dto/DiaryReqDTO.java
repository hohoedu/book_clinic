package com.hohoedu.book_clinic.diary._dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 독서일지 요청 DTO 모음 (2026-07-30) */
public class DiaryReqDTO {

    /** 일괄 저장 요청 — 상단 "저장" 버튼. 화면에서 실제로 값이 바뀐 행만 담아 보낸다 */
    @Data
    public static class BulkSaveReqDTO {
        @NotEmpty(message = "저장할 내용이 없습니다.")
        @Valid
        private List<SaveItemDTO> items;
    }

    /**
     * 일괄 저장 항목 1건 — 독서태도(복수 선택)와 기타 전달사항을 세션 1건 단위로 저장한다.
     * 도움 필요(help_needed)는 여기에 없다 — 모니터링에서만 켜고 끄는 학생 상태값이라 이 화면은
     * 표시만 하고, 저장 SQL(upsertDiaryNote)도 그 컬럼을 건드리지 않는다.
     */
    @Data
    public static class SaveItemDTO {
        @NotNull(message = "세션 ID는 필수입니다.")
        private Integer sessionId;

        @NotBlank(message = "학생 ID는 필수입니다.")
        private String studentId;

        private List<String> attitudeCodes;

        @Size(max = 500, message = "전달사항은 500자까지 입력할 수 있습니다.")
        private String memo;
    }

    /**
     * 등/하원 시각 변경 요청 — 일지 헤더(erp_bookstore_diary)의 in_time/out_time만 갱신한다.
     * 세션(입·퇴실 원본)은 건드리지 않는다: 학생 앱이 찍은 실제 로그인/퇴실 시각은 이력으로 남겨두고,
     * 직원 보정값은 일지 쪽에만 반영하는 게 스키마 주석의 정책("일지 작성 시점 스냅샷, 직원 보정 가능")이다.
     * 값은 화면 입력칸과 같은 "HH:mm" 문자열이며, 빈 문자열/null은 "미기록"(NULL 저장)을 뜻한다.
     */
    @Data
    public static class TimeReqDTO {
        @NotNull(message = "세션 ID는 필수입니다.")
        private Integer sessionId;

        @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "등원 시각 형식이 올바르지 않습니다.")
        private String inTime;

        @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "하원 시각 형식이 올바르지 않습니다.")
        private String outTime;
    }

}
