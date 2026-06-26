package com.hohoedu.book_clinic.question._dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 문제 관련 요청 DTO 모음 */
public class QuestionReqDTO {

    /** 문제 등록 요청 */
    @Data
    public static class RegisterReqDTO {
        @NotNull(message = "마스터 도서 ID는 필수입니다.")
        private Integer contentId;
        @NotBlank(message = "레벨은 필수입니다.")
        private String qlevel;
        @NotBlank(message = "문제 번호는 필수입니다.")
        private String qnum;
        private String q;
        private String qex;
        private String e1;
        private String e2;
        private String e3;
        private String e4;
        private String ans;
        private String qtype;
        private String qexgb;
        private String state;
    }

    /** 문제 수정 요청 (contentId + qlevel + qnum으로 대상 특정) */
    @Data
    public static class UpdateReqDTO {
        @NotNull(message = "마스터 도서 ID는 필수입니다.")
        private Integer contentId;
        @NotBlank(message = "레벨은 필수입니다.")
        private String qlevel;
        @NotBlank(message = "문제 번호는 필수입니다.")
        private String qnum;
        private String q;
        private String qex;
        private String e1;
        private String e2;
        private String e3;
        private String e4;
        private String ans;
        private String qtype;
        private String qexgb;
        private String state;
    }

    /** 문제 삭제 요청 */
    @Data
    public static class DeleteReqDTO {
        @NotNull(message = "마스터 도서 ID는 필수입니다.")
        private Integer contentId;
        @NotBlank(message = "레벨은 필수입니다.")
        private String qlevel;
        @NotBlank(message = "문제 번호는 필수입니다.")
        private String qnum;
    }

    /** 문제 복구 요청 */
    @Data
    public static class RestoreReqDTO {
        @NotNull(message = "삭제 이력 ID는 필수입니다.")
        private Integer delId;
    }

}
