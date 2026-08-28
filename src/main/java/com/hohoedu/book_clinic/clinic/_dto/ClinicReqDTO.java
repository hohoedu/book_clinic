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
        private String qlevel; // 01=기본(완독/레벨 처리), 02=심화(이력 기록만) — 생략 시 01
        /**
         * 재제출 종류 (2026-08-28) — 생략/그 외 값이면 서버가 제출 회차로 FIRST/RETRY를 스스로 판단한다.
         *   WRONG_ONLY = "틀린 문제 다시 풀기" — 점수/등급/뱃지 어떤 것도 바꾸지 않는다(화면 표시만)
         *   RETRY      = "재도전" — 최종 점수·grade·뱃지를 "올라갈 때만" 갱신(null→FRIEND→KING). 처음 점수는 고정
         */
        private String mode;
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
        private String qtype;
    }

}
