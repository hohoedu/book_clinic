package com.hohoedu.book_clinic.clinic._dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 학생 독서 클리닉 요청 DTO 모음 */
public class ClinicReqDTO {

    /** 책 추천 요청 */
    @Data
    public static class RecommendReqDTO {
        @NotBlank(message = "학생 ID는 필수입니다.")
        private String studentId;
    }

    /**
     * 문제풀이 채점 제출 요청 (qlevel=01 기본 / 02 심화)
     * 정답 수/총 문항 수를 클라이언트가 계산해서 보내던 방식은 조작 가능(devtools로 correctCount만
     * 바꿔서 제출 가능)해서, 문항별 선택 답안을 보내면 서버가 erp_bookstore_itempool.ans와
     * 직접 대조해서 채점한다 (2026-07-09 서버 검증으로 전환).
     */
    @Data
    public static class QuizSubmitReqDTO {
        @NotBlank(message = "학생 ID는 필수입니다.")
        private String studentId;
        @NotNull(message = "도서 ID는 필수입니다.")
        private Integer contentId;
        private String qlevel; // 01=기본(완독/EXP 처리), 02=심화(이력 기록만) — 생략 시 01
        @NotEmpty(message = "제출한 답안이 없습니다.")
        @Valid
        private List<AnswerDTO> answers;
    }

    /** 문항별 제출 답안 (qnum + 선택한 보기 번호) */
    @Data
    public static class AnswerDTO {
        @NotBlank(message = "문제 번호는 필수입니다.")
        private String qnum;
        @NotNull(message = "선택한 답은 필수입니다.")
        private Integer selected;
    }

    /** 문제 풀이 이력(erp_bookstore_quiz_answer_log) 저장용 — 제출 답안 + 서버 채점 결과 스냅샷 */
    @Data
    public static class AnswerLogDTO {
        private String qnum;
        private Integer selected;
        private boolean correct;
    }

}
