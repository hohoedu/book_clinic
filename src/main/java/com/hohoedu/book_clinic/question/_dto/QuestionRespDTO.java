package com.hohoedu.book_clinic.question._dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 문제 관련 응답 DTO 모음 */
public class QuestionRespDTO {

    /** 엑셀 업로드 결과 */
    @Data
    public static class UploadResultDTO {
        private List<DuplicateInfo> duplicates = new ArrayList<>();
        private int total;
        private int activeCount;
        private int inactiveCount;
        public boolean hasDuplicates() { return !duplicates.isEmpty(); }
    }

    /** 중복 문제 정보 */
    @Data
    public static class DuplicateInfo {
        private Integer contentId;
        private String qlevel;
        private String qnum;
        private String bookTitle;
    }

    /** 문제 조회 응답 */
    @Data
    public static class QuestionDTO {
        private Integer contentId;
        private String qlevel;
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

    /** 삭제된 문제 조회 응답 */
    @Data
    public static class QuestionDelDTO {
        private Integer delId;
        private LocalDateTime deletedAt;
        private String deletedBy;
        private Integer contentId;
        private String qlevel;
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

}
