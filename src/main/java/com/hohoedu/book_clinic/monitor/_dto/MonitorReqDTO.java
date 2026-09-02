package com.hohoedu.book_clinic.monitor._dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 실시간 모니터링 요청 DTO 모음 */
public class MonitorReqDTO {

    /** 퇴실 처리 요청 — 세션ID를 몰라도 되도록 studentId만 받는다 (오늘 열린 세션을 서버가 찾음) */
    @Data
    public static class ExitReqDTO {
        @NotBlank(message = "학생 ID는 필수입니다.")
        private String studentId;
    }

    /** 독서일지 저장 요청 — 세션 1건당 upsert (erp_bookstore_diary + erp_bookstore_attitude) */
    @Data
    public static class DiaryReqDTO {
        @NotNull(message = "세션 ID는 필수입니다.")
        private Integer sessionId;
        @NotBlank(message = "학생 ID는 필수입니다.")
        private String studentId;
        private List<String> attitudeCodes;  // 독서 태도 체크(복수 선택) — attitude 테이블에 1행씩 저장
        private Boolean helpNeeded;          // 도움 필요 여부 ("혼자 읽기 어려워요") — 선택지가 하나뿐이라 플래그
        // erp_bookstore_diary.memo가 VARCHAR(500)이라 그대로 넘기면 DataIntegrityViolationException으로
        // 500 크래시가 났다(2026-08-20 스트레스 테스트로 발견) — diary._dto.DiaryReqDTO.SaveItemDTO에는
        // 이미 있던 같은 제약을 여기도 맞췄다.
        @Size(max = 500, message = "전달사항은 500자까지 입력할 수 있습니다.")
        private String memo;                 // 기타 전달사항
    }

    /**
     * 문제풀이 기록 삭제(초기화) 요청 — 학생이 "지워주세요"라고 하면 직원이 모니터링 카드에서 누른다.
     * 지울 회차를 정확히 집기 위해 recommendId로 받고, studentId는 그 추천이 정말 이 학생 것인지
     * 서버에서 대조하는 용도다(임의의 recommendId를 밀어넣는 것을 막는다).
     */
    @Data
    public static class QuizResetReqDTO {
        @NotNull(message = "추천 ID는 필수입니다.")
        private Integer recommendId;
        @NotBlank(message = "학생 ID는 필수입니다.")
        private String studentId;
    }

    /**
     * 추천 도서 교체 요청 (2026-09-02) — 추천된 책이 실제로 서가에 없거나 못 읽을 정도로 훼손됐을 때
     * 직원이 모니터링 카드에서 누른다. 지금 추천을 없애고 다음 책을 바로 추천한다.
     * reason은 재고에서 뺀 사유를 이력에 남기기 위한 값으로, 처리 자체는 두 사유가 동일하다
     * (둘 다 "그 한 권을 재고에서 뺀다") — 구분해두는 건 나중에 되돌릴 가능성이 다르기 때문이다.
     */
    @Data
    public static class CancelRecommendReqDTO {
        @NotNull(message = "추천 ID는 필수입니다.")
        private Integer recommendId;
        @NotBlank(message = "학생 ID는 필수입니다.")
        private String studentId;
        /** MISSING(책이 없음) / DAMAGED(훼손) — quiz_reset_log.log_type에 그대로 남는다 */
        @NotBlank(message = "사유는 필수입니다.")
        private String reason;
    }

}
