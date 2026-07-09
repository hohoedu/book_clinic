package com.hohoedu.book_clinic.clinic._dto;

import lombok.Data;

public class ClinicRespDTO {

    /** 추천 도서 카드 */
    @Data
    public static class RecommendBookDTO {
        private Integer contentId;
        private String originalTitle;
        private String author;
        private String publisher;
        private String summary;
        private String contentType;
        private String contentTypeName;  // 분류명 (교과연계 등)
        private String genre;            // 장르코드 (erp_bookstore_code.gubun='G')
        private String genreName;        // 장르명
        private String keywords;         // 콤마 구분 키워드 (화면에서 태그로 변환)
        private String imageUrl;
        private String curriculumName;   // 연계교과 (detail C)
        private String recommendOrgName; // 추천기관 (detail R)
        private String awardName;        // 수상명 (detail A)
    }

    /** 직전 추천 도서의 분류/장르 (연속 추천 시 중복 배제 판정 기준) */
    @Data
    public static class LastRecommendDTO {
        private String contentType;
        private String genre;
    }

    /** 레벨 마스터 (진행률/레벨업 계산용) */
    @Data
    public static class LevelDTO {
        private Integer levelNo;
        private Integer requiredExp;
    }

    /** 학생+도서의 추천 기록 상태 (없으면 null) */
    @Data
    public static class RecommendLogStatusDTO {
        private Integer recommendId;
        private String status;  // PENDING / DONE
        private String grade;   // KING / FRIEND / null
    }

    /** 학생 누적 EXP/레벨 (student_info 행이 없으면 null) */
    @Data
    public static class StudentExpDTO {
        private Integer exp;
        private Integer levelNo;
    }

    /** 기본 문제풀이(qlevel=01) 채점 결과 */
    @Data
    public static class QuizSubmitRespDTO {
        private boolean passed;        // 합격선(2/3) 이상 여부
        private String grade;          // KING(독서왕) / FRIEND(독서친구) / null(재도전)
        private int correctCount;
        private int totalCount;
        private int passLine;          // 합격에 필요한 최소 정답 수
        private boolean alreadyCompleted;  // 이미 DONE 처리된 책을 재제출한 경우 (EXP 재지급 없음)
        private Integer expGained;     // 이번에 획득한 EXP (재도전/이미완료면 null 또는 0)
        private Integer levelNo;       // 갱신 후 현재 레벨
        private boolean leveledUp;     // 이번 제출로 레벨이 올랐는지
    }

}
