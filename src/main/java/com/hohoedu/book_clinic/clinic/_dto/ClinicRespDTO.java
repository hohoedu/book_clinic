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

    /** 학생+도서의 추천 기록 상태 (없으면 null) */
    @Data
    public static class RecommendLogStatusDTO {
        private Integer recommendId;
        private String status;  // PENDING / DONE
        private String grade;   // KING / FRIEND / null
    }

    /** student-main 화면 레벨 카드에 내려줄 최종 계산 결과 */
    @Data
    public static class MainLevelInfoDTO {
        private Integer levelNo;
        private String levelName;          // 단계명 (카드에 "{levelName} 단계"로 표시)
        private String title;              // 레벨 칭호
        private String feature;
        private Integer progressPercent;   // 현재 레벨 구간 내 진행률 (0~100)
        private Integer booksToNextLevel;  // 다음 레벨까지 남은 완독 권수 (만렙이면 0)
    }

    /** student-main "이번 달에 읽은 책" 패널 1건 (완료 도서 + 현재 읽는 중인 도서 1건) */
    @Data
    public static class MonthBookDTO {
        private Integer contentId;
        private String originalTitle;
        private String imageUrl;
        private String status;  // DONE(완료) / PENDING(읽는 중)
    }

    /**
     * 카드 1장 — NORMAL(완독한 책 1권, 책당 고정 1종. cardName=책 제목, bookTitle=저자) 또는
     * RARE(NORMAL 카드 10장마다 추가 지급, 특정 책과 무관 — contentId/bookTitle 없음).
     */
    @Data
    public static class CardDTO {
        private Integer contentId;
        private String cardType;    // NORMAL / RARE
        private String cardName;    // NORMAL=책 제목, RARE="레어 카드" (화면 strong)
        private String bookTitle;   // NORMAL=저자, RARE=null (화면 small)
        private String imageUrl;    // NORMAL=책 표지, RARE=고정 레어카드 이미지
    }

    /** student-main "나의 카드 컬렉션" 패널 — 보유 카드 목록 + 10장당 실물 1장 진행도 */
    @Data
    public static class CardCollectionDTO {
        private List<CardDTO> cards;         // 보유 카드(완독 책), 최신순
        private int totalCards;              // 보유 카드 총 수
        private int exchangeableCount;       // 실물 교환 가능 횟수 (totalCards / 10)
        private int cardsToNextReward;       // 다음 실물까지 남은 카드 수 (10 - totalCards % 10)
    }

    /** 기본 문제풀이(qlevel=01) 채점 결과 */
    @Data
    public static class QuizSubmitRespDTO {
        private boolean passed;        // 합격선(2/3) 이상 여부
        private String grade;          // KING(독서왕) / FRIEND(독서친구) / null(재도전)
        private int correctCount;
        private int totalCount;
        private int passLine;          // 합격에 필요한 최소 정답 수
        private boolean alreadyCompleted;  // 이미 DONE 처리된 책을 재제출한 경우 (레벨 재계산 없음)
        private Integer levelNo;           // 이번 완독 반영 후 현재 레벨 (합격 시에만, 아니면 null)
        private String levelTitle;         // 현재 레벨 칭호 (합격 시에만, 미시딩이면 null)
        private boolean leveledUp;         // 이번 완독으로 레벨이 올랐는지
        private Integer progressPercent;   // 현재 레벨 구간 내 진행률 (0~100, 합격 시에만)
        private Integer booksToNextLevel;  // 다음 레벨까지 남은 완독 권수 (만렙이면 0, 합격 시에만)
        private List<BadgeDTO> newBadges;  // 이번 제출로 새로 획득한 뱃지 (결과 화면 팝업용, 없으면 빈 목록)
        // 온라인 카드 — 이번 제출로 새 완독(DONE)이 되어 카드를 새로 획득한 경우에만 채워진다
        private String cardName;           // 획득 카드명(=책 제목), 신규 획득이 아니면 null
        private String cardImageUrl;       // 획득 카드 이미지(=책 표지)
        private Integer totalCards;         // 획득 후 보유 카드 총 수
        private boolean cardRewardReached;  // 이번 획득으로 10장 세트를 채웠는지(실물 1장 교환 시점)
    }

    /** 뱃지 마스터 1건 — 달성 조건은 category+threshold+param으로 데이터화 (erp_bookstore_badge) */
    @Data
    public static class BadgeDTO {
        private Integer badgeId;
        private String badgeName;
        private String badgeDesc;   // 달성 조건/특징 문구 (화면 표시용)
        private String category;    // BASIC_ATTEMPT / BASIC_PASS / BASIC_PERFECT / ADV_PASS / ADV_PERFECT
        private Integer threshold;  // 모두 1(처음 1회 달성 시 획득하는 단발 업적)
        private String param;       // 현재 미사용
    }

}
