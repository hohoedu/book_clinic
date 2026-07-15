package com.hohoedu.book_clinic.monitor._dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 실시간 모니터링 요청 DTO 모음 */
public class MonitorReqDTO {

    /** 퇴실 처리 요청 — 세션ID를 몰라도 되도록 studentId만 받는다 (오늘 열린 세션을 서버가 찾음) */
    @Data
    public static class ExitReqDTO {
        @NotBlank(message = "학생 ID는 필수입니다.")
        private String studentId;
    }

    /** 독서일지 저장 요청 — 세션 1건당 upsert */
    @Data
    public static class ReadingLogReqDTO {
        @NotNull(message = "세션 ID는 필수입니다.")
        private Integer sessionId;
        @NotBlank(message = "학생 ID는 필수입니다.")
        private String studentId;
        private List<String> attitudeCodes;  // 독서 태도 체크(복수 선택)
        private String helpNeeded;           // 도움 필요 코드 (없으면 null)
        private String note;                 // 기타 전달사항
    }

}
