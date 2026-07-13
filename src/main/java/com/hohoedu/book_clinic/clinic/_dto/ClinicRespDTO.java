package com.hohoedu.book_clinic.clinic._dto;

import java.util.List;

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

    /** 레벨 마스터 1건 상세 (레벨명/특징문구/다음 레벨까지 필요한 누적 EXP) */
    @Data
    public static class LevelDetailDTO {
        private Integer levelNo;
        private String levelName;   // 입문 / 성장 / 마스터
        private String feature;
        private Integer requiredExp;
    }

    /** student-main 화면 레벨 카드에 내려줄 최종 계산 결과 */
    @Data
    public static class MainLevelInfoDTO {
        private Integer levelNo;
        private String levelName;
        private String feature;
        private Integer progressPercent;   // 현재 레벨 구간 내 진행률 (0~100)
        private Integer booksToNextLevel;  // 다음 레벨까지 남은 EXP를 학생 학년 권당 EXP로 환산한 예상 권수 (만렙이면 0)
    }

    /** student-main "이번 달에 읽은 책" 패널 1건 (완료 도서 + 현재 읽는 중인 도서 1건) */
    @Data
    public static class MonthBookDTO {
        private Integer contentId;
        private String originalTitle;
        private String imageUrl;
        private String status;  // DONE(완료) / PENDING(읽는 중)
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
        private List<BadgeDTO> newBadges;  // 이번 제출로 새로 획득한 뱃지 (결과 화면 팝업용, 없으면 빈 목록)
    }

    /** 뱃지 마스터 1건 — 달성 조건은 category+threshold+param으로 데이터화 (erp_bookstore_badge) */
    @Data
    public static class BadgeDTO {
        private Integer badgeId;
        private String badgeName;
        private String badgeDesc;   // 달성 조건 문구 (화면 표시용)
        private String category;    // FIRST_BOOK / MONTH_STREAK / CROWN / QTYPE_PERFECT / TOPIC / ADV_PERFECT / META
        private Integer threshold;
        private String param;       // QTYPE_PERFECT=문제유형 T코드 / TOPIC=콤마 구분 키워드
    }

    /** 문제유형(T코드)별 "합격 회차 전 문항 정답" 달성 책 수 (QTYPE_PERFECT 판정용) */
    @Data
    public static class QtypePerfectDTO {
        private String qtype;
        private Integer cnt;
    }

    /** 월별 완독(DONE) 권수 (MONTH_STREAK 판정용) — yearMonth는 'yyyy-MM' */
    @Data
    public static class MonthCompletionDTO {
        private String yearMonth;
        private Integer count;
    }

}
