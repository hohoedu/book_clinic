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

    /** 실시간 모니터링 화면 최초 진입용 — 예약 기준 카드 목록(로그인 직원의 센터로 스코핑). 이후 갱신은 Firestore 구독으로 받는다 */
    public MonitorRespDTO.LiveViewRespDTO getLiveView(LocalDate date, String centerCode) {
        List<MonitorRespDTO.CardDTO> cards = monitorRepository.findReservationCards(date, centerCode);
        cards.forEach(this::fillDerivedFields);

        MonitorRespDTO.LiveViewRespDTO resp = new MonitorRespDTO.LiveViewRespDTO();
        resp.setCards(cards);
        resp.setCounts(buildCounts(cards));
        return resp;
    }

    /**
     * 독서일지 저장(upsert) — 직원이 카드 우측 패널에서 입력.
     * 헤더는 세션 1건당 1행이고, 태도 체크는 체크 해제까지 반영해야 해서 전량 삭제 후 재삽입한다.
     */
    @Transactional
    public void saveDiary(MonitorReqDTO.DiaryReqDTO req, String staffName) {
        monitorRepository.upsertDiary(req.getSessionId(), Boolean.TRUE.equals(req.getHelpNeeded()),
                req.getMemo(), staffName);

        Integer diaryKey = monitorRepository.findDiaryKeyBySessionId(req.getSessionId());
        if (diaryKey != null) {
            monitorRepository.deleteDiaryAttitudes(diaryKey);
            List<String> codes = req.getAttitudeCodes();
            if (codes != null && !codes.isEmpty()) {
                monitorRepository.insertDiaryAttitudes(diaryKey, req.getStudentId(), codes);
            }
        }
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
     * 결과 화면 진입 시각 기록 — StudentViewController.getResultPage()가 호출한다.
     * 채점 제출 시 "문제 푸는 중"이 이미 해제된 뒤라, 이후 카드 상태가 "결과 확인중"으로 바뀐다
     * (홈으로/재도전 등 결과 화면을 벗어나면 해제된다).
     */
    @Transactional
    public void markResultViewing(String studentId) {
        Integer sessionId = monitorRepository.findOpenSessionId(studentId, LocalDate.now());
        if (sessionId == null) return;
        monitorRepository.markResultViewing(sessionId);
        syncSafely(sessionId);
    }

    /**
     * 결과 화면 이탈 시 "결과 확인중" 해제 — StudentViewController.getStudentMainPage()(홈으로)가 호출한다.
     * 재도전(문제풀이 재진입)은 markQuizStarted가 result_viewed_at을 함께 비우므로 여기서 처리하지 않는다.
     * 열린 세션이 없으면(로그인 직후 아직 책 추천 전 등) 조용히 넘어간다.
     */
    @Transactional
    public void clearResultViewing(String studentId) {
        Integer sessionId = monitorRepository.findOpenSessionId(studentId, LocalDate.now());
        if (sessionId == null) return;
        monitorRepository.clearResultViewing(sessionId);
        syncSafely(sessionId);
    }

    /**
     * 채점 결과를 독서일지 상세에 자동 적재 — ClinicService.submitQuiz가 제출 직후 호출한다.
     * 직원이 입력하는 값이 아니라 "그날 무슨 책을 몇 분 읽고 몇 점 맞았는지"를 시스템이 남기는 기록이다.
     * 일지 헤더가 아직 없으면(직원이 일지를 쓰기 전) 세션 정보로 헤더부터 만든다.
     * 오늘 열린 세션이 없으면(예약 없이 API를 직접 호출한 경우 등) 남길 일지가 없으므로 조용히 넘어간다.
     * syncStudentToday()가 quiz_started_at을 지우기 전에 호출되어야 경과 시간 계산이 어긋나지 않는다.
     */
    @Transactional
    public void recordDiaryDetail(String studentId, Integer contentId, Integer recommendId,
                                  String qlevel, int correctCount, int totalCount) {
        Integer sessionId = monitorRepository.findOpenSessionId(studentId, LocalDate.now());
        if (sessionId == null) return;

        monitorRepository.ensureDiary(sessionId);
        Integer diaryKey = monitorRepository.findDiaryKeyBySessionId(sessionId);
        if (diaryKey == null) return;

        monitorRepository.upsertDiaryDetail(diaryKey, contentId, recommendId, qlevel, correctCount, totalCount);
    }

    /**
     * 채점 제출 완료 후 호출(ClinicService.submitQuiz) — 채점하는 순간 바로 "결과 확인중"으로
     * 전환한다("문제 푸는 중" 해제 + 결과 확인 시각 기록을 한 번에). 제출 직후 학생이 결과 화면으로
     * 넘어가는데, 결과 페이지 GET(markResultViewing)만 믿으면 그 요청이 오기 전까지 "독서중"으로
     * 잠깐 찍히므로, 채점 시점에 상태를 먼저 확정한다.
     * 세션ID를 모르는 호출부를 위해 오늘 그 학생의 열린 세션을 스스로 찾아 Firestore에 반영한다.
     * 열린 세션이 없으면(예약 없이 직접 API를 호출한 경우 등) 반영할 카드가 없으므로 조용히 넘어간다.
     */
    @Transactional
    public void syncStudentToday(String studentId) {
        Integer sessionId = monitorRepository.findOpenSessionId(studentId, LocalDate.now());
        if (sessionId == null) return;
        monitorRepository.clearQuizStarted(sessionId);
        monitorRepository.markResultViewing(sessionId);
        syncSafely(sessionId);
    }

    private void syncSafely(Integer sessionId) {
        if (sessionId == null) return;
        try {
            MonitorRespDTO.CardDTO card = monitorRepository.findCardBySessionId(sessionId);
            if (card == null) {
                log.warn("Firestore 동기화 건너뜀 — sessionId={}에 해당하는 카드 없음", sessionId);
                return;
            }
            fillDerivedFields(card);
            monitorSyncService.syncCard(card);
            // 성공 여부를 서버 로그에서 바로 확인할 수 있게 info로 남긴다(실시간 반영 디버깅용)
            log.info("Firestore 동기화 성공 — sessionId={}, studentId={}, cardStatus={}, sessionDate={}",
                    sessionId, card.getStudentId(), card.getCardStatus(), card.getSessionDate());
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
        card.setBooks(fillBookPages(card));
    }

    /** 카드 캐러셀용 — 그 학생이 오늘 추천받은 책 목록을 조회하고, 책마다 권장 분을 파싱해 채운다 */
    private List<MonitorRespDTO.BookPageDTO> fillBookPages(MonitorRespDTO.CardDTO card) {
        if (card.getSessionDate() == null) return List.of();
        List<MonitorRespDTO.BookPageDTO> books = monitorRepository.findTodayBooks(card.getStudentId(), card.getSessionDate());
        books.forEach(book -> book.setReadingTimeMinutes(parseMinutes(book.getReadingTimeText())));
        return books;
    }

    /** 우선순위: 미입실 > 퇴실 > 문제 푸는 중 > 결과 확인중 > 재도전 필요 > 권장시간 초과 > 독서 중(추천 직후 기본값) */
    private String resolveCardStatus(MonitorRespDTO.CardDTO card, Integer readingTimeMinutes, Integer elapsedMinutes) {
        if (card.getSessionId() == null) {
            return "NOT_ENTERED";
        }
        if ("EXITED".equals(card.getSessionStatus())) {
            return "EXITED";
        }
        if (card.getQuizStartedAt() != null) {
            return "QUIZ_IN_PROGRESS";
        }
        // 채점 제출 후 결과 화면을 보고 있는 상태 — 불합격(RETRY_NEEDED 조건)이어도 결과 화면을 보는 동안은
        // 이 상태가 우선한다(홈으로 나가면 clearResultViewing으로 해제되어 재도전 필요로 넘어간다)
        if (card.getResultViewedAt() != null) {
            return "RESULT_VIEWING";
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
                case "NOT_ENTERED" -> counts.setNotEntered(counts.getNotEntered() + 1);
                case "READING" -> counts.setReading(counts.getReading() + 1);
                case "QUIZ_IN_PROGRESS" -> counts.setQuizInProgress(counts.getQuizInProgress() + 1);
                case "RESULT_VIEWING" -> counts.setResultViewing(counts.getResultViewing() + 1);
                case "TIME_OVER" -> counts.setTimeOver(counts.getTimeOver() + 1);
                case "RETRY_NEEDED" -> counts.setRetryNeeded(counts.getRetryNeeded() + 1);
                default -> { /* EXITED는 별도 chip 없음 */ }
            }
            // 미입실 카드는 아직 독서일지 대상이 아니므로 미등록 카운트에서 제외
            if (card.getDiaryKey() == null && !"NOT_ENTERED".equals(card.getCardStatus())) {
                counts.setReadingLogMissing(counts.getReadingLogMissing() + 1);
            }
        }
        return counts;
    }

}
