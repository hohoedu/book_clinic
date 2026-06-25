package com.hohoedu.book_clinic.bookstore.question._dto;

import lombok.Data;

/** 문제 관련 요청 DTO 모음 */
public class QuestionReqDTO {

    /** 문제 등록 요청 */
    @Data
    public static class RegisterReqDTO {
        private Integer contentId;
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

    /** 문제 수정 요청 (contentId + qnum으로 대상 특정) */
    @Data
    public static class UpdateReqDTO {
        private Integer contentId;
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
        private Integer contentId;
        private String qnum;
    }

    /** 문제 복구 요청 */
    @Data
    public static class RestoreReqDTO {
        private Integer delId;
    }

}
