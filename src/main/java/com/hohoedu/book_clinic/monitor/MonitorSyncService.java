package com.hohoedu.book_clinic.monitor;

import java.util.HashMap;
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
        doc.put("sessionStatus", card.getSessionStatus());
        doc.put("sessionDate", card.getSessionDate() == null ? null : card.getSessionDate().toString());
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
}
