package com.hohoedu.book_clinic.clinic._dto;

import java.util.List;

import lombok.Data;

public class ClinicRespDTO {

    /** 우선순위/재고/dedup 필터를 통과한 item(실물 판본) 1건 — pickNextItem 결과 (2026-07-30) */
    @Data
    public static class PickedItemDTO {
        private Integer itemId;
        private Integer contentId;
    }

    /** 추천 도서 카드 */
    @Data
    public static class RecommendBookDTO {
        /** 이 추천이 실제로 대여 확정된 item(실물 판본) — 재입실 재대여 등에 쓰인다 */
        private Integer itemId;
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

    /**
     * 심화 게이트 판정용 (2026-08-31) — 가장 최근에 완독(DONE)한 추천의 도서와 완독 날짜(KST).
     * "책 추천받기"로 다음 책을 받으려면 이 책의 심화(qlevel=02)를 1회 이상 풀었어야 한다.
     * 단 완독일이 오늘이 아니면(날이 바뀌면) 심화 미응시여도 다음 책을 허용한다.
     */
    @Data
    public static class AdvancedGateDTO {
        private Integer contentId;
        private java.time.LocalDate completedDate;
    }

    /**
     * 홈 화면(student-main) 진입 시 상태 — 2026-07-29 재설계로 "책 다 읽으면 홈 진입만으로 바로
     * 다음 책 자동 추천"을 없애고, 학생이 직접 "책 추천받기"를 눌러야 다음 책이 나가도록 바꿨다.
     *   READING       — 아직 안 끝낸(PENDING) 책이 있음. book=그 책, 버튼="문제 풀기"
     *   AWAITING_NEXT — 직전 책은 끝냈고(DONE) 다음 책 추천 전. book=마지막으로 끝낸 책, 버튼="책 추천받기"
     * (생애 첫 로그인은 book이 아예 없어 애매하므로 서버가 그 자리에서 즉시 추천해 READING으로 내려준다)
     */
    @Data
    public static class BookStatusRespDTO {
        private String state;
        private RecommendBookDTO book;
    }

    /** 학생+도서의 추천 기록 상태 (없으면 null) */
    @Data
    public static class RecommendLogStatusDTO {
        private Integer recommendId;
        private String status;  // PENDING(추천됨, 첫 제출 전) / DONE(첫 제출 완료)
        private String grade;   // KING / FRIEND / null — 재도전 최종 결과로 갱신됨
        // 이미 완독(DONE)한 책을 재제출했을 때 "그때 받은 점수"를 그대로 다시 보여주기 위해 함께 읽는다
        // (2026-08-20 — 이 값이 없어서 재제출분의 즉석 채점 결과가 grade와 어긋나 표시됐다)
        private Integer correctCount;       // "처음 점수" (최초 제출값 고정)
        private Integer finalCorrectCount;  // "최종 점수" (재도전 최신값, 2026-08-28)
        private Integer totalCount;
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
        private boolean passed;        // 이번 제출이 합격선(2/3) 이상인지 (재도전이면 이번 재도전 기준)
        private String grade;          // 이번 제출 기준 KING / FRIEND / null — recommend_log.grade도 이 값으로 갱신됨(재도전)
        private int attemptNo;         // 이번 제출이 몇 번째 시도인지(1=첫 시도, 2=재도전 1회차...)
        private int correctCount;      // 이번 제출 정답 수 (화면 표시용)
        private Integer firstCorrectCount;  // "처음 점수" — 최초 제출값 (2026-08-28, 재도전 화면에서 처음/최종 비교용)
        private Integer finalCorrectCount;  // "최종 점수" — 재도전 반영 최신값 (2026-08-28)
        private int totalCount;
        private int passLine;          // 합격에 필요한 최소 정답 수
        // 이번 제출에서 틀린 문항 번호 — "틀린 문제 풀기"가 쓴다. 화면이 정답(itempool.ans)을
        // 직접 대조해 만들던 값을 서버 계산으로 옮긴 것이다(2026-08-20, 정답 노출 차단).
        private List<String> wrongQnums;
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
        private Integer stepNow;            // 독서탐험 진행 칸 수 = 올해 완독 권수 (합격 시에만)
        private Integer stepTotal;          // 독서탐험 전체 칸 수 = 학년별 목표 권수 (합격 시에만)
    }

    /** 특정 recommend_id+qlevel의 문항별 "가장 최근 제출" 정답 여부 — 부분 재제출(틀린 문제만 다시 풀기) 시
     *  이번에 다시 제출하지 않은 문항의 정답 여부를 이어받기 위해 조회한다(2026-08-25) */
    @Data
    public static class LatestAnswerDTO {
        private String qnum;
        private Boolean correct;
    }

    /** 완독(KING/FRIEND) 후 홈 화면 "완료 화면"에 내려줄 상태 — 남은 액션(틀린 문제 다시 풀기/심화
     *  문제 풀기) 유무와 그 책 정보(2026-08-25) */
    @Data
    public static class CompletionStateDTO {
        private RecommendBookDTO book;
        private List<String> wrongQnums;
        private boolean advancedAvailable;
        // 심화 재도전/틀린문제 다시풀기(2026-08-31) — 심화를 1회 이상 풀었고 아직 심화왕이 아니면 true.
        private boolean advancedRetryAvailable;
        private List<String> advancedWrongQnums;
        // 결과화면/완료화면 버튼 분기용 (2026-08-28) — KING=심화만, FRIEND=재도전/틀린문제/심화,
        // null(불합격)=재도전만. 재도전으로 합격하면 이 값이 갱신되어 버튼도 바뀐다.
        private String grade;
        // 지금 "책 추천받기"를 눌러 다음 책을 받을 수 있는 상태인지 (2026-08-28) — 오늘 추천 한도를
        // 다 썼으면 false → 프론트에서 버튼 자체를 숨긴다.
        private boolean canRecommendNext;
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
