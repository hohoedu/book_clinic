package com.hohoedu.book_clinic.monitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.google.cloud.firestore.Firestore;
import com.hohoedu.book_clinic.monitor._dto.MonitorRespDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SQL이 원본인 모니터링 카드 상태를 Firestore `clinic_monitor/{reservationId}` 문서로 미러링한다.
 * 관리자 브라우저는 이 컬렉션을 onSnapshot으로 구독해 실시간 갱신을 받는다 (2026-07-15).
 *
 * [왜 @Async인가(2026-08-26)] 원래는 호출부(MonitorService)의 @Transactional 메서드 끝에서 이
 * 메서드를 동기(.get())로 기다렸다. 그동안 DB 커넥션을 계속 붙잡고 있어서, Firestore 왕복이
 * 조금만 느려져도 커넥션 풀이 고갈되어 이 화면과 무관한 다른 요청까지 전부 느려지는 문제가 있었다
 * (학생 페이지 진입마다 호출 빈도가 늘면서 체감될 정도로 커짐). 전용 스레드 풀(AsyncConfig)에서
 * 돌려 트랜잭션 커밋 직후 커넥션을 바로 반납하고, 사용자 응답도 Firestore를 기다리지 않게 한다.
 * 대신 실패를 호출부가 더 이상 동기로 catch할 수 없으므로, 이 메서드 안에서 직접 로그로 남긴다
 * — Firestore 쓰기 실패가 SQL 트랜잭션에 영향을 주면 안 된다는 원칙은 그대로 유지된다.
 *
 * [문서 키가 sessionId가 아니라 reservationId인 이유(2026-08-20)] 예약 생성 시점엔 아직 세션이
 * 없어(sessionId=null) 그때는 동기화가 아예 안 됐었다 — 예약 완료 직후 모니터링에 안 뜨는
 * 버그의 원인. reservationId는 예약 생성 순간부터 세션 생성 전까지 쭉 존재하고 하루에 유일하므로,
 * 예약→입실→퇴실 전 구간에서 항상 같은 문서 하나를 갱신하도록 문서 키를 이걸로 통일했다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorSyncService {

    private static final String COLLECTION = "clinic_monitor";

    private final Firestore firestore;

    @Async("firestoreSyncExecutor")
    public void syncCard(MonitorRespDTO.CardDTO card) {
        if (card.getReservationId() == null) {
            log.warn("Firestore 동기화 건너뜀 — reservationId 없는 카드: studentId={}", card.getStudentId());
            return;
        }
        Map<String, Object> doc = new HashMap<>();
        doc.put("reservationId", card.getReservationId());
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
        doc.put("basicFinalCorrectCount", card.getBasicFinalCorrectCount());
        doc.put("basicTotalCount", card.getBasicTotalCount());
        doc.put("basicStatus", card.getBasicStatus());
        doc.put("basicGrade", card.getBasicGrade());
        doc.put("quizQlevel", card.getQuizQlevel());
        doc.put("basicAttemptRounds", card.getBasicAttemptRounds());
        doc.put("advancedCorrectCount", card.getAdvancedCorrectCount());
        doc.put("advancedTotalCount", card.getAdvancedTotalCount());
        doc.put("badgeCount", card.getBadgeCount());
        doc.put("latestBadgeName", card.getLatestBadgeName());
        doc.put("diaryKey", card.getDiaryKey());
        doc.put("attitudeCodes", card.getAttitudeCodes());
        doc.put("helpNeeded", card.getHelpNeeded());
        doc.put("memo", card.getMemo());
        doc.put("elapsedMinutes", card.getElapsedMinutes());
        doc.put("cardStatus", card.getCardStatus());
        // books는 BookPageDTO POJO 리스트라 그대로 넣으면 안 된다 — 그 안의 LocalDateTime(recommendedAt)을
        // Firestore Admin SDK가 리플렉션 직렬화하려다 JDK 모듈 시스템(java.time.chrono 미개방)에 막혀
        // InaccessibleObjectException으로 터진다(2026-07-24 실시간 반영 전면 실패 원인). 최상위 필드처럼
        // LocalDateTime을 ISO 문자열로 flatten한 Map 리스트로 변환해서 넣는다.
        doc.put("books", toBookMaps(card.getBooks()));

        try {
            firestore.collection(COLLECTION).document(String.valueOf(card.getReservationId())).set(doc).get();
            log.info("Firestore 동기화 성공 — reservationId={}, studentId={}, cardStatus={}",
                    card.getReservationId(), card.getStudentId(), card.getCardStatus());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Firestore 동기화 실패 — SQL은 정상 반영됨: reservationId={}", card.getReservationId(), e);
        } catch (ExecutionException e) {
            log.warn("Firestore 동기화 실패 — SQL은 정상 반영됨: reservationId={}", card.getReservationId(), e);
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
            // 여기 빠뜨린 필드는 최초 진입(/admin/monitor/live)에만 보이고 실시간 갱신에선 사라진다 —
            // 프론트가 읽는 BookPageDTO 필드를 하나도 빠짐없이 넣어야 한다(2026-07-31)
            m.put("recommendId", book.getRecommendId());
            m.put("contentId", book.getContentId());
            m.put("bookTitle", book.getBookTitle());
            m.put("author", book.getAuthor());
            m.put("publisher", book.getPublisher());
            m.put("imageUrl", book.getImageUrl());
            m.put("readingTimeText", book.getReadingTimeText());
            m.put("recommendedAt", toIso(book.getRecommendedAt()));
            m.put("basicCorrectCount", book.getBasicCorrectCount());
            m.put("basicFinalCorrectCount", book.getBasicFinalCorrectCount());
            m.put("basicTotalCount", book.getBasicTotalCount());
            m.put("basicStatus", book.getBasicStatus());
            m.put("basicGrade", book.getBasicGrade());
            m.put("advancedCorrectCount", book.getAdvancedCorrectCount());
            m.put("advancedTotalCount", book.getAdvancedTotalCount());
            m.put("readingTimeMinutes", book.getReadingTimeMinutes());
            m.put("elapsedMinutes", book.getElapsedMinutes());
            // 책별 뱃지(stat-row 획득 뱃지 칸) — 같은 이유로 빠져 있어서 실시간 갱신 때만 뱃지가
            // 사라졌다가 새로고침하면 다시 보였다
            m.put("badgeCount", book.getBadgeCount());
            m.put("latestBadgeName", book.getLatestBadgeName());
            result.add(m);
        }
        return result;
    }
}
