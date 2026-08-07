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
        private String centerCode;      // 학생 소속 센터 — 모니터링 센터별 스코핑/Firestore 구독 필터 기준
        private String sessionStatus;   // ENTERED / EXITED (raw, 미입실이면 null)
        private LocalDate sessionDate;  // 예약일(=입실일, Firestore 구독 시 날짜 필터 기준)
        private String timeSlot;        // 예약 교시('1'~'4') — monitor-live.js 슬롯 필터 기준
        private LocalDateTime enteredAt;
        private LocalDateTime exitedAt;
        private LocalDateTime quizStartedAt; // null이 아니면 지금 문제풀이 화면에 진입해 있는 상태
        private LocalDateTime resultViewedAt; // null이 아니면 지금 결과 화면을 보고 있는 상태(채점 제출 후)

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

        private Integer diaryKey;       // null이면 독서일지 미등록
        private String attitudeCodes;   // attitude 행들을 콤마로 묶은 값
        private Boolean helpNeeded;
        private String memo;

        // elapsedMinutes는 SQL이 DATEDIFF(MINUTE, recommended_at, CURRENT_TIMESTAMP)로 직접 계산해서
        // 채워준다(Java의 LocalDateTime.now()를 쓰지 않음 — DB/JVM 타임존이 어긋나면 오차가 생기므로,
        // recommended_at과 "지금"을 반드시 같은 시계(DB)로 비교한다). readingTimeMinutes/cardStatus만
        // MonitorService가 계산해서 채운다.
        private Integer readingTimeMinutes; // readingTimeText에서 파싱한 권장 분(파싱 실패 시 null)
        private Integer elapsedMinutes;     // DB에서 DATEDIFF로 계산된 경과 분 (recommendedAt 없으면 null)
        private String cardStatus;          // NOT_ENTERED / READING / QUIZ_IN_PROGRESS / RESULT_VIEWING / RETRY_NEEDED / TIME_OVER / EXITED

        // 오늘 이 학생이 추천받은 책 전체(완료분 포함) — 카드 안 도서 캐러셀용 (2026-07-23).
        // 위쪽 root의 bookTitle 등은 그중 최신 1건과 항상 같은 값이며, 구버전 Firestore 문서 호환을
        // 위해 계속 채워둔다. books가 비어있으면(예: 미입실) 프론트가 root 필드로 폴백한다.
        private List<BookPageDTO> books;
    }

    /**
     * 카드 캐러셀의 책 1페이지 — CardDTO의 book 관련 필드와 동일한 의미를 그날 추천받은 책마다
     * 각각 갖는다(획득 뱃지처럼 학생 단위 통계는 여기 없고 CardDTO에만 있다).
     */
    @Data
    public static class BookPageDTO {
        // 여러 학생 것을 한 쿼리로 묶어 받을 때(findTodayBooksByStudentIds) 어느 카드 것인지
        // 가르는 용도 — 단건 조회(findTodayBooks)에서는 안 채워도 된다(2026-08-07).
        private String studentId;
        // 이 페이지가 어느 추천(도전)인지 — 문제풀이 기록 삭제(resetQuiz)의 대상 식별자다.
        // 같은 책을 나중에 다시 추천받으면 content_id는 같아도 recommend_id가 달라서, 지울 회차를
        // 정확히 집으려면 content_id가 아니라 이 값을 써야 한다.
        private Integer recommendId;
        private Integer contentId;
        private String bookTitle;
        private String author;
        private String publisher;
        private String imageUrl;
        private String readingTimeText;
        private LocalDateTime recommendedAt;

        private Integer basicCorrectCount;
        private Integer basicTotalCount;
        private String basicStatus;

        private Integer advancedCorrectCount;
        private Integer advancedTotalCount;

        private Integer readingTimeMinutes; // MonitorService가 readingTimeText에서 파싱
        private Integer elapsedMinutes;     // DB DATEDIFF

        // 이 책(content_id)에서 획득한 뱃지만 집계 — 학생 전체 뱃지가 아니다(과거엔 CardDTO의
        // badgeCount/latestBadgeName이 학생 전체 뱃지였어서, A책 카드에도 B책에서 딴 뱃지가 같이
        // 보이는 문제가 있었다). 2026-07-29
        private Integer badgeCount;
        private String latestBadgeName;
    }

    /** 문제풀이 기록 삭제 대상 1건 — 초기화 직전 recommend_log 스냅샷(삭제 이력에 그대로 남긴다) */
    @Data
    public static class QuizResetTargetDTO {
        private Integer recommendId;
        private String studentId;
        private Integer contentId;
        private Integer itemId;     // 그때 대여했던 실물 판본 — 되돌릴 때 같은 책을 다시 확보하는 기준
        private Integer correctCount;
        private Integer totalCount;
        private String grade;
        private String status;
    }

    /**
     * 문제풀이 기록 삭제 결과 — 직원이 곧바로 알아야 하는 건 "실물 책을 지금 줄 수 있느냐"다.
     * 되돌린 책을 그 사이 다른 학생이 가져갔을 수 있어서(A가 끝낸 책을 B가 추천받은 경우),
     * 시스템이 실물을 확보했는지 여부를 화면에 그대로 알려준다.
     */
    @Data
    public static class QuizResetRespDTO {
        private int cancelledCount;      // 함께 취소된 뒤 추천 권수
        private boolean bookSecured;     // 실물 확보 성공 — 학생에게 책을 건네주면 된다
        private boolean copySwitched;    // 원래 판본이 없어 같은 책의 다른 사본으로 대체됨
    }

    /** 독서태도 코드 옵션(use_yn=1만) — 화면 체크박스 렌더링용. erp_bookstore_attitude_code 조회 결과 */
    @Data
    public static class AttitudeCodeDTO {
        private String attitudeCode;
        private String attitudeName;
    }

    /** 필터 chip 카운트 */
    @Data
    public static class CountsDTO {
        private int total;
        private int notEntered;
        private int reading;
        private int quizInProgress;
        private int resultViewing;
        private int timeOver;
        private int retryNeeded;
        private int readingLogMissing;
    }

    /** 실시간 모니터링 화면 최초 진입용 응답 (Firestore 구독 전 초기 렌더링) */
    @Data
    public static class LiveViewRespDTO {
        private List<CardDTO> cards;
        private CountsDTO counts;
        // 독서일지 패널의 태도 체크박스 목록(use_yn=1만) — DB 값을 고치면 재배포 없이 화면에 반영된다.
        private List<AttitudeCodeDTO> attitudeCodeOptions;
    }

}
