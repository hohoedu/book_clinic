package com.hohoedu.book_clinic.clinic._dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

public class ClinicRespDTO {

    /** 학생 이름 + 독서 현황 + 레벨 정보 (학생 메인 상단/레벨 카드) */
    @Getter
    @Setter
    public static class StudentInfoDTO {
        private String studentId;
        private String studentName;
        private Integer levelNo;
        private String levelName;      // 단계명 (입문/성장/마스터)
        private String title;          // 칭호
        private String feature;        // 단계 특징 문구
        private Integer exp;           // 누적 경험치
        private Integer booksRead;     // 누적 완독 수
        private Integer requiredExp;   // 현재 레벨에서 다음 레벨로 가는 데 필요한 누적 EXP
        private String characterImg;   // 선택 캐릭터 이미지 (없으면 null)

        // 서비스에서 계산해서 채우는 값
        private Integer booksToNextLevel;  // 다음 레벨까지 남은 권수 (학생 학년 권당 EXP 기준)
        private Integer progressPercent;   // 현재 레벨 구간 내 진행률 (0~100)
    }

    /** 획득 뱃지 (레벨 카드 아래 패널) */
    @Getter
    @Setter
    public static class BadgeDTO {
        private String badgeName;
        private String description;
        private String imageUrl;
        private LocalDateTime acquiredAt;
    }

    /** 이번 달에 읽은 책 (하단 패널) */
    @Getter
    @Setter
    public static class MonthBookDTO {
        private Integer contentId;
        private String originalTitle;
        private String imageUrl;
        private String status;         // READING / DONE
    }

    /** 오늘의 추천 도서 카드 */
    @Getter
    @Setter
    public static class RecommendBookDTO {
        private Integer contentId;
        private String originalTitle;
        private String author;
        private String publisher;
        private String summary;
        private String contentTypeName;  // 분류명 (교과연계 등)
        private String genreName;        // 장르명
        private String keywords;         // 콤마 구분 키워드 (화면에서 태그로 변환)
        private String imageUrl;
        private String curriculumName;   // 연계교과 (detail C)
        private String recommendOrgName; // 추천기관 (detail R)
        private String awardName;        // 수상명 (detail A)
    }

    /** 최근 완독 도서의 분류/장르 (추천 스킵 판정 기준) */
    @Getter
    @Setter
    public static class LastReadDTO {
        private String contentType;
        private String genre;
    }

    /** 레벨 마스터 (진행률 계산용) */
    @Getter
    @Setter
    public static class LevelDTO {
        private Integer levelNo;
        private Integer requiredExp;
    }
}
