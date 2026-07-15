package com.hohoedu.book_clinic.monitor;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hohoedu.book_clinic.monitor._dto.MonitorReqDTO;
import com.hohoedu.book_clinic.monitor._dto.MonitorRespDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 실시간 모니터링 — 입실/퇴실 세션 관리, 카드 파생값 계산, 독서일지 저장 (2026-07-15)
 * SQL 반영 직후 Firestore 미러링(MonitorSyncService)까지 이 서비스가 책임진다. Firestore 쓰기
 * 실패는 로그만 남기고 삼킨다 — 원본(SQL) 트랜잭션은 이미 커밋된 뒤라 서비스 동작에 영향 없음.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorService {

    /** content.reading_time("20분" 등 자유 텍스트)에서 선행 숫자만 안전하게 파싱 */
    private static final Pattern MINUTES_PATTERN = Pattern.compile("\\d+");

    private final MonitorRepository monitorRepository;
    private final MonitorSyncService monitorSyncService;

    /**
     * 입실 기록 — 학생 로그인 성공 시 StudentViewController가 호출한다.
     * 오늘 이미 열린(ENTERED) 세션이 있으면 재사용하고, 없으면(당일 첫 로그인 또는 이전
     * 세션이 이미 퇴실 처리됨) 새로 만든다.
     */
    @Transactional
    public void enterSession(String studentId) {
        LocalDate today = LocalDate.now();
        Integer sessionId = monitorRepository.findOpenSessionId(studentId, today);
        if (sessionId == null) {
            monitorRepository.insertSession(studentId, today);
            sessionId = monitorRepository.findOpenSessionId(studentId, today);
        }
        syncSafely(sessionId);
    }

    /**
     * 퇴실 처리 — 직원이 모니터링 화면에서 호출. enterSession과 대칭 구조로 studentId만 받아서
     * 오늘 열린 세션을 스스로 찾는다. 열린 세션이 없으면(이미 퇴실 처리됨 등) 조용히 무시.
     */
    @Transactional
    public void exitSession(String studentId) {
        Integer sessionId = monitorRepository.findOpenSessionId(studentId, LocalDate.now());
        if (sessionId == null) {
            log.info("퇴실 처리 요청 무시 — 열린 세션 없음: studentId={}", studentId);
            return;
        }
        monitorRepository.updateSessionExit(sessionId);
        syncSafely(sessionId);
    }

    /** 실시간 모니터링 화면 최초 진입용 — 이후 갱신은 Firestore 구독으로 받는다 */
    public MonitorRespDTO.LiveViewRespDTO getLiveView(LocalDate date) {
        List<MonitorRespDTO.CardDTO> cards = monitorRepository.findSessionCards(date);
        cards.forEach(this::fillDerivedFields);

        MonitorRespDTO.LiveViewRespDTO resp = new MonitorRespDTO.LiveViewRespDTO();
        resp.setCards(cards);
        resp.setCounts(buildCounts(cards));
        return resp;
    }

    /** 독서일지 저장(upsert) — 직원이 카드 우측 패널에서 입력 */
    @Transactional
    public void saveReadingLog(MonitorReqDTO.ReadingLogReqDTO req, String staffName) {
        String attitudeCodes = req.getAttitudeCodes() == null || req.getAttitudeCodes().isEmpty()
                ? null : String.join(",", req.getAttitudeCodes());
        monitorRepository.upsertReadingLog(req.getSessionId(), req.getStudentId(), attitudeCodes,
                req.getHelpNeeded(), req.getNote(), staffName);
        syncSafely(req.getSessionId());
    }

    /**
     * 문제풀이 화면 진입 시각 기록 — StudentViewController.getQuestionPage()가 호출한다.
     * 이후 카드 상태가 "문제 푸는 중"으로 바뀐다(제출하면 clearQuizStarted로 해제).
     */
    @Transactional
    public void markQuizStarted(String studentId) {
        Integer sessionId = monitorRepository.findOpenSessionId(studentId, LocalDate.now());
        if (sessionId == null) return;
        monitorRepository.markQuizStarted(sessionId);
        syncSafely(sessionId);
    }

    /**
     * 채점 제출 완료 후 호출(ClinicService.submitQuiz) — "문제 푸는 중" 상태를 해제하고
     * 세션ID를 모르는 호출부를 위해 오늘 그 학생의 열린 세션을 스스로 찾아 Firestore에 반영한다.
     * 열린 세션이 없으면(예약 없이 직접 API를 호출한 경우 등) 반영할 카드가 없으므로 조용히 넘어간다.
     */
    @Transactional
    public void syncStudentToday(String studentId) {
        Integer sessionId = monitorRepository.findOpenSessionId(studentId, LocalDate.now());
        if (sessionId == null) return;
        monitorRepository.clearQuizStarted(sessionId);
        syncSafely(sessionId);
    }

    private void syncSafely(Integer sessionId) {
        if (sessionId == null) return;
        try {
            MonitorRespDTO.CardDTO card = monitorRepository.findCardBySessionId(sessionId);
            if (card == null) return;
            fillDerivedFields(card);
            monitorSyncService.syncCard(card);
        } catch (Exception e) {
            log.warn("Firestore 동기화 실패 — SQL은 정상 반영됨: sessionId={}", sessionId, e);
        }
    }

    /**
     * 권장시간 파싱 + 카드 상태 계산. elapsedMinutes는 SQL(DATEDIFF)이 이미 채워서 내려주므로
     * 여기서는 그 값을 그대로 쓴다 — Java 쪽에서 다시 "지금"을 구해 비교하면 DB 서버 시계와
     * 어긋날 수 있어서 일부러 하지 않는다.
     */
    private void fillDerivedFields(MonitorRespDTO.CardDTO card) {
        Integer readingTimeMinutes = parseMinutes(card.getReadingTimeText());
        card.setReadingTimeMinutes(readingTimeMinutes);
        card.setCardStatus(resolveCardStatus(card, readingTimeMinutes, card.getElapsedMinutes()));
    }

    /** 우선순위: 퇴실 > 문제 푸는 중 > 재도전 필요 > 권장시간 초과 > 독서 중(추천 직후 기본값) */
    private String resolveCardStatus(MonitorRespDTO.CardDTO card, Integer readingTimeMinutes, Integer elapsedMinutes) {
        if ("EXITED".equals(card.getSessionStatus())) {
            return "EXITED";
        }
        if (card.getQuizStartedAt() != null) {
            return "QUIZ_IN_PROGRESS";
        }
        // 기본 문제풀이를 이미 한 번 제출했는데 불합격(PENDING)인 경우만 재도전 필요로 본다
        // (아직 한 번도 안 푼 경우는 correctCount가 null이라 여기 안 걸리고 READING 유지)
        if ("PENDING".equals(card.getBasicStatus()) && card.getBasicCorrectCount() != null) {
            return "RETRY_NEEDED";
        }
        if (readingTimeMinutes != null && elapsedMinutes != null && elapsedMinutes > readingTimeMinutes) {
            return "TIME_OVER";
        }
        return "READING";
    }

    private Integer parseMinutes(String readingTimeText) {
        if (readingTimeText == null) return null;
        Matcher m = MINUTES_PATTERN.matcher(readingTimeText);
        return m.find() ? Integer.parseInt(m.group()) : null;
    }

    private MonitorRespDTO.CountsDTO buildCounts(List<MonitorRespDTO.CardDTO> cards) {
        MonitorRespDTO.CountsDTO counts = new MonitorRespDTO.CountsDTO();
        counts.setTotal(cards.size());
        for (MonitorRespDTO.CardDTO card : cards) {
            switch (card.getCardStatus()) {
                case "READING" -> counts.setReading(counts.getReading() + 1);
                case "QUIZ_IN_PROGRESS" -> counts.setQuizInProgress(counts.getQuizInProgress() + 1);
                case "TIME_OVER" -> counts.setTimeOver(counts.getTimeOver() + 1);
                case "RETRY_NEEDED" -> counts.setRetryNeeded(counts.getRetryNeeded() + 1);
                default -> { /* EXITED는 별도 chip 없음 */ }
            }
            if (card.getReadingLogId() == null) {
                counts.setReadingLogMissing(counts.getReadingLogMissing() + 1);
            }
        }
        return counts;
    }

}
