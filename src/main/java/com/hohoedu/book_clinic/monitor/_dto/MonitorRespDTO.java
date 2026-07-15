package com.hohoedu.book_clinic.monitor._dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

public class MonitorRespDTO {

    /**
     * 실시간 모니터링 카드 1건 — MonitorMapper.findSessionCards/findCardBySessionId로
     * 원본(raw) 값을 채운 뒤, MonitorService에서 elapsedMinutes/cardStatus 등 파생값을 채운다.
     * 이 DTO를 그대로 Firestore 문서로도 직렬화한다(MonitorSyncService).
     */
    @Data
    public static class CardDTO {
        private Integer sessionId;
        private String studentId;
        private String studentName;
        private String sessionStatus;   // ENTERED / EXITED (raw)
        private LocalDate sessionDate;  // 입실일 (Firestore 구독 시 날짜 필터 기준)
        private LocalDateTime enteredAt;
        private LocalDateTime exitedAt;
        private LocalDateTime quizStartedAt; // null이 아니면 지금 문제풀이 화면에 진입해 있는 상태

        private Integer recommendId;    // 이 학생의 최신 추천 도서 (없으면 아직 추천 전)
        private Integer contentId;
        private String bookTitle;
        private String author;
        private String publisher;
        private String imageUrl;
        private String readingTimeText;      // content.reading_time 원문 (예: "20분")
        private LocalDateTime recommendedAt; // 이 책이 추천/대여 확정된 시각 = 독서 시작 기준

        private Integer basicCorrectCount;
        private Integer basicTotalCount;
        private String basicStatus;     // PENDING / DONE (raw, null이면 아직 안 풂)

        private Integer advancedCorrectCount;
        private Integer advancedTotalCount;

        private Integer badgeCount;
        private String latestBadgeName;

        private Integer readingLogId;   // null이면 독서일지 미등록
        private String attitudeCodes;   // 콤마 구분
        private String helpNeeded;
        private String note;

        // elapsedMinutes는 SQL이 DATEDIFF(MINUTE, recommended_at, CURRENT_TIMESTAMP)로 직접 계산해서
        // 채워준다(Java의 LocalDateTime.now()를 쓰지 않음 — DB/JVM 타임존이 어긋나면 오차가 생기므로,
        // recommended_at과 "지금"을 반드시 같은 시계(DB)로 비교한다). readingTimeMinutes/cardStatus만
        // MonitorService가 계산해서 채운다.
        private Integer readingTimeMinutes; // readingTimeText에서 파싱한 권장 분(파싱 실패 시 null)
        private Integer elapsedMinutes;     // DB에서 DATEDIFF로 계산된 경과 분 (recommendedAt 없으면 null)
        private String cardStatus;          // READING / QUIZ_IN_PROGRESS / RETRY_NEEDED / TIME_OVER / EXITED
    }

    /** 필터 chip 카운트 */
    @Data
    public static class CountsDTO {
        private int total;
        private int reading;
        private int quizInProgress;
        private int timeOver;
        private int retryNeeded;
        private int readingLogMissing;
    }

    /** 실시간 모니터링 화면 최초 진입용 응답 (Firestore 구독 전 초기 렌더링) */
    @Data
    public static class LiveViewRespDTO {
        private List<CardDTO> cards;
        private CountsDTO counts;
    }

}
