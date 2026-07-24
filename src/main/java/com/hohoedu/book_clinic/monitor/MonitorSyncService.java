package com.hohoedu.book_clinic.monitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.springframework.stereotype.Service;

import com.google.cloud.firestore.Firestore;
import com.hohoedu.book_clinic.monitor._dto.MonitorRespDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SQL이 원본인 모니터링 카드 상태를 Firestore `clinic_monitor/{sessionId}` 문서로 미러링한다.
 * 관리자 브라우저는 이 컬렉션을 onSnapshot으로 구독해 실시간 갱신을 받는다 (2026-07-15).
 * Firestore 쓰기 실패는 원본 SQL 트랜잭션에 영향을 주면 안 되므로 호출부(MonitorService)에서
 * 항상 try/catch로 감싸 호출한다 — 이 서비스 자체는 실패를 그대로 던진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorSyncService {

    private static final String COLLECTION = "clinic_monitor";

    private final Firestore firestore;

    public void syncCard(MonitorRespDTO.CardDTO card) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("sessionId", card.getSessionId());
        doc.put("studentId", card.getStudentId());
        doc.put("studentName", card.getStudentName());
        doc.put("centerCode", card.getCenterCode());
        doc.put("sessionStatus", card.getSessionStatus());
        doc.put("sessionDate", card.getSessionDate() == null ? null : card.getSessionDate().toString());
        doc.put("timeSlot", card.getTimeSlot());
        doc.put("enteredAt", toIso(card.getEnteredAt()));
        doc.put("exitedAt", toIso(card.getExitedAt()));
        doc.put("recommendId", card.getRecommendId());
        doc.put("contentId", card.getContentId());
        doc.put("bookTitle", card.getBookTitle());
        doc.put("author", card.getAuthor());
        doc.put("publisher", card.getPublisher());
        doc.put("imageUrl", card.getImageUrl());
        doc.put("readingTimeText", card.getReadingTimeText());
        doc.put("readingTimeMinutes", card.getReadingTimeMinutes());
        doc.put("recommendedAt", toIso(card.getRecommendedAt()));
        doc.put("basicCorrectCount", card.getBasicCorrectCount());
        doc.put("basicTotalCount", card.getBasicTotalCount());
        doc.put("basicStatus", card.getBasicStatus());
        doc.put("advancedCorrectCount", card.getAdvancedCorrectCount());
        doc.put("advancedTotalCount", card.getAdvancedTotalCount());
        doc.put("badgeCount", card.getBadgeCount());
        doc.put("latestBadgeName", card.getLatestBadgeName());
        doc.put("readingLogId", card.getReadingLogId());
        doc.put("attitudeCodes", card.getAttitudeCodes());
        doc.put("helpNeeded", card.getHelpNeeded());
        doc.put("note", card.getNote());
        doc.put("elapsedMinutes", card.getElapsedMinutes());
        doc.put("cardStatus", card.getCardStatus());
        // books는 BookPageDTO POJO 리스트라 그대로 넣으면 안 된다 — 그 안의 LocalDateTime(recommendedAt)을
        // Firestore Admin SDK가 리플렉션 직렬화하려다 JDK 모듈 시스템(java.time.chrono 미개방)에 막혀
        // InaccessibleObjectException으로 터진다(2026-07-24 실시간 반영 전면 실패 원인). 최상위 필드처럼
        // LocalDateTime을 ISO 문자열로 flatten한 Map 리스트로 변환해서 넣는다.
        doc.put("books", toBookMaps(card.getBooks()));

        try {
            // 동기 대기로 처리 — 실패 시 예외가 그대로 던져지도록 해서 호출부(MonitorService)의
            // try/catch가 실제로 실패를 잡을 수 있게 한다 (fire-and-forget이면 실패가 조용히 묻힘)
            firestore.collection(COLLECTION).document(String.valueOf(card.getSessionId())).set(doc).get();
            log.debug("Firestore 카드 동기화: sessionId={}, cardStatus={}", card.getSessionId(), card.getCardStatus());
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Firestore 동기화 실패: sessionId=" + card.getSessionId(), e);
        }
    }

    private String toIso(java.time.LocalDateTime dt) {
        return dt == null ? null : dt.toString();
    }

    /**
     * BookPageDTO 리스트를 Firestore가 안전하게 직렬화할 수 있는 Map 리스트로 변환한다.
     * LocalDateTime(recommendedAt)은 ISO 문자열로 flatten — POJO 그대로 넘기면 SDK가 java.time을
     * 리플렉션으로 직렬화하려다 모듈 시스템에 막힌다. 프론트(monitor-live.js bookPages)가 읽는 키와
     * 이름을 맞춘다.
     */
    private List<Map<String, Object>> toBookMaps(List<MonitorRespDTO.BookPageDTO> books) {
        if (books == null) return List.of();
        List<Map<String, Object>> result = new ArrayList<>(books.size());
        for (MonitorRespDTO.BookPageDTO book : books) {
            Map<String, Object> m = new HashMap<>();
            m.put("contentId", book.getContentId());
            m.put("bookTitle", book.getBookTitle());
            m.put("author", book.getAuthor());
            m.put("publisher", book.getPublisher());
            m.put("imageUrl", book.getImageUrl());
            m.put("readingTimeText", book.getReadingTimeText());
            m.put("recommendedAt", toIso(book.getRecommendedAt()));
            m.put("basicCorrectCount", book.getBasicCorrectCount());
            m.put("basicTotalCount", book.getBasicTotalCount());
            m.put("basicStatus", book.getBasicStatus());
            m.put("advancedCorrectCount", book.getAdvancedCorrectCount());
            m.put("advancedTotalCount", book.getAdvancedTotalCount());
            m.put("readingTimeMinutes", book.getReadingTimeMinutes());
            m.put("elapsedMinutes", book.getElapsedMinutes());
            result.add(m);
        }
        return result;
    }
}
