package com.hohoedu.book_clinic.monitor;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.handler.exception.Exception404;
import com.hohoedu.book_clinic._core.utils.KstClock;
import com.hohoedu.book_clinic.book.BookService;
import com.hohoedu.book_clinic.monitor._dto.MonitorReqDTO;
import com.hohoedu.book_clinic.monitor._dto.MonitorRespDTO;
import com.hohoedu.book_clinic.pass.PassService;

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
    private final BookService bookService;
    private final PassService passService;

    /**
     * 입실 기록 — 학생 로그인 성공 시 StudentViewController가 호출한다.
     * 오늘 이미 열린(ENTERED) 세션이 있으면 재사용하고, 없으면(당일 첫 로그인 또는 이전
     * 세션이 이미 퇴실 처리됨) 새로 만든다.
     *
     * 이용권 차감(2026-08-05)도 여기서 함께 한다 — getHomeState/recommendBook 세 호출부가
     * 전부 이 메서드를 거치는 유일한 "입실" 지점이라, 차감 규칙을 여기 한 곳에 두면 호출부마다
     * 중복해서 검사할 필요가 없다. consume()은 그날 이미 썼으면 다시 까지 않고 그대로 잔여값을
     * 돌려주므로(0이어도) 반복 호출에 안전하다 — 새로 깎을 게 없는데 오늘 아직 한 번도 못 쓴
     * 경우에만 -1이 오므로, 그 경우에만 입실을 막는다. 예외가 나면 @Transactional이 방금 만든
     * 세션 insert까지 함께 롤백해서 "이용권도 없는데 세션만 남는" 상태가 생기지 않는다.
     */
    @Transactional
    public void enterSession(String studentId) {
        LocalDate today = KstClock.today();
        Integer sessionId = monitorRepository.findOpenSessionId(studentId, today);
        if (sessionId == null) {
            monitorRepository.insertSession(studentId, today);
            sessionId = monitorRepository.findOpenSessionId(studentId, today);
        }
        if (passService.consume(studentId, "BOOK", sessionId) == -1) {
            throw new Exception400("이용권이 모두 소진되었습니다. 재결제 후 이용해주세요.");
        }
        // 독서일지가 "메인 데이터"가 되도록, 직원이 뭔가 저장하기 전이라도 입실 시점에 바로 헤더를
        // 만들어둔다(2026-07-30) — 예전엔 첫 저장/제출 전까지 diary 행이 없어서, 화면이 세션값
        // COALESCE 폴백으로만 채워지는 불안정한 상태였다. ensureDiary는 이미 있으면 손대지 않는다.
        monitorRepository.ensureDiary(sessionId);
        syncSafely(sessionId);
    }

    /**
     * 퇴실 처리 — 직원이 모니터링 화면에서 호출. enterSession과 대칭 구조로 studentId만 받아서
     * 오늘 열린 세션을 스스로 찾는다. 열린 세션이 없으면(이미 퇴실 처리됨 등) 조용히 무시.
     */
    @Transactional
    public void exitSession(String studentId) {
        Integer sessionId = monitorRepository.findOpenSessionId(studentId, KstClock.today());
        if (sessionId == null) {
            log.info("퇴실 처리 요청 무시 — 열린 세션 없음: studentId={}", studentId);
            return;
        }
        monitorRepository.updateSessionExit(sessionId);
        // 완독 전이라도 클리닉을 나가면 그 사이 다른 학생이 재고를 못 쓰는 문제를 막기 위해, 완독
        // 여부와 무관하게 퇴실 시점에 대여 중인 책을 반납한다(2026-07-29). 재입실하면 ClinicService
        // (getHomeState/recommendBook의 ensureActiveLoan)가 같은 책을 다시 대여해 이어 읽게 한다.
        bookService.returnActiveLoanByStudent(studentId);
        // diary가 메인 데이터가 되려면 퇴실 시각도 diary에 실제로 남아야 한다 — enterSession의
        // ensureDiary가 만들어둔 헤더에 out_time을 채운다(2026-07-30, 직원이 수동 보정한 값은 보존).
        monitorRepository.syncDiaryOutTime(sessionId);
        syncSafely(sessionId);
    }

    /** 실시간 모니터링 화면 최초 진입용 — 예약 기준 카드 목록(로그인 직원의 센터로 스코핑). 이후 갱신은 Firestore 구독으로 받는다 */
    public MonitorRespDTO.LiveViewRespDTO getLiveView(LocalDate date, String centerCode) {
        List<MonitorRespDTO.CardDTO> cards = monitorRepository.findReservationCards(date, centerCode);
        cards.forEach(this::fillDerivedFields);

        MonitorRespDTO.LiveViewRespDTO resp = new MonitorRespDTO.LiveViewRespDTO();
        resp.setCards(cards);
        resp.setCounts(buildCounts(cards));
        resp.setAttitudeCodeOptions(monitorRepository.findActiveAttitudeCodes());
        return resp;
    }

    /**
     * 독서일지 저장(upsert) — 직원이 카드 우측 패널에서 입력.
     * 헤더는 세션 1건당 1행이고, 태도 체크는 체크 해제까지 반영해야 해서 전량 삭제 후 재삽입한다.
     *
     * 도움 필요(helpNeeded)는 두 군데에 저장한다(2026-07-30):
     *   · erp_student.help_needed  — 풀릴 때까지 유지되는 학생 상태값. 다음 수업에도 켜진 채로 보인다.
     *   · erp_bookstore_diary.help_needed — 그날 일지의 스냅샷. 나중에 상태를 풀어도 과거 일지엔 남는다.
     * 화면(모니터링 카드)이 읽는 값은 상태값 쪽이다(MonitorMapper의 st.help_needed).
     */
    @Transactional
    public void saveDiary(MonitorReqDTO.DiaryReqDTO req, String staffName) {
        boolean helpNeeded = Boolean.TRUE.equals(req.getHelpNeeded());
        monitorRepository.updateStudentHelpNeeded(req.getStudentId(), helpNeeded);
        monitorRepository.upsertDiary(req.getSessionId(), helpNeeded, req.getMemo(), staffName);

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
        Integer sessionId = monitorRepository.findOpenSessionId(studentId, KstClock.today());
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
        Integer sessionId = monitorRepository.findOpenSessionId(studentId, KstClock.today());
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
        Integer sessionId = monitorRepository.findOpenSessionId(studentId, KstClock.today());
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
        Integer sessionId = monitorRepository.findOpenSessionId(studentId, KstClock.today());
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
        Integer sessionId = monitorRepository.findOpenSessionId(studentId, KstClock.today());
        if (sessionId == null) return;
        monitorRepository.clearQuizStarted(sessionId);
        monitorRepository.markResultViewing(sessionId);
        syncSafely(sessionId);
    }

    /**
     * 문제풀이 기록 삭제(초기화) — 학생이 "지워주세요"라고 하면 직원이 모니터링 카드에서 누른다 (2026-07-31).
     * 삭제한 그 책을 "지금 읽는 책"으로 되돌려서, 학생이 홈에 들어오면 그 책 문제를 처음부터 다시 푼다.
     *
     * 대상 책(RESET):
     *   · quiz_answer_log(기본+심화 전 회차) 삭제
     *   · 그 책에서 딴 뱃지 / NORMAL 카드 회수
     *   · 그 도전으로 적재된 독서일지 상세 삭제
     *   · recommend_log를 PENDING·정답수 null로 되돌림 (행 자체는 남긴다 — 지우면 대여했던
     *     실물 item_id와의 연결이 끊겨 학생 홈이 읽던 책을 못 찾는다)
     * 뱃지를 함께 회수하는 이유는 ClinicService의 "첫 시도" 판정이 quiz_answer_log 유무로 이뤄지기
     * 때문이다 — 로그만 지우고 뱃지를 남기면 다시 풀 때 같은 책 뱃지가 한 번 더 나간다.
     *
     * 그 뒤에 추천받은 책(CANCEL): 학생 홈은 가장 최근 PENDING 추천을 "지금 읽는 책"으로 보여주므로,
     * 뒤 책이 남아있으면 되돌린 책이 계속 가려진다. 그래서 뒤 추천은 기록만이 아니라 recommend_log
     * 행까지 지워 없던 추천으로 만든다(하루 추천 권수 상한 countTodayRecommends도 함께 줄어든다).
     * 그 책들로 딴 뱃지·카드도 근거가 사라지므로 같이 회수한다.
     *
     * 실물 처리: 뒤 추천을 취소했다면 지금 대여 중인 책(마지막 추천 책)을 반납하고, 되돌린 책의
     * 실물을 그 자리에서 다시 확보한다. 완독으로 이미 반납됐던 책이면 그 사이 다른 학생이 가져갔을
     * 수 있어서(A가 끝낸 책을 B가 추천받은 경우), 같은 책의 다른 사본까지 찾아본다
     * (BookService.secureCopyForStudent). 한 권도 없으면 확보 없이 진행한다 — 문제는 content 기준으로
     * 출제되어 실물 없이도 풀 수 있다. 대신 그 사실을 응답으로 돌려줘 직원이 바로 알게 한다.
     *
     * 지운 값은 erp_bookstore_quiz_reset_log에 스냅샷으로 남긴다(누가·언제·무엇을).
     */
    @Transactional
    public MonitorRespDTO.QuizResetRespDTO resetQuiz(MonitorReqDTO.QuizResetReqDTO req, String staffName) {
        MonitorRespDTO.QuizResetTargetDTO target =
                monitorRepository.findQuizResetTarget(req.getRecommendId(), req.getStudentId());
        if (target == null) {
            throw new Exception404("삭제할 문제풀이 기록을 찾을 수 없습니다: recommendId=" + req.getRecommendId());
        }

        // 1) 뒤에 추천받은 책부터 없앤다 — 자식(풀이 이력/일지 상세)을 먼저 지워야 FK가 걸리지 않는다
        List<MonitorRespDTO.QuizResetTargetDTO> later =
                monitorRepository.findLaterRecommends(target.getStudentId(), target.getRecommendId());
        for (MonitorRespDTO.QuizResetTargetDTO cancelled : later) {
            int answers = monitorRepository.deleteQuizAnswerLogs(cancelled.getRecommendId());
            int badges = monitorRepository.deleteStudentBadges(cancelled.getStudentId(), cancelled.getContentId());
            int cards = monitorRepository.deleteNormalCard(cancelled.getStudentId(), cancelled.getContentId());
            monitorRepository.deleteDiaryDetailByRecommend(cancelled.getRecommendId());
            monitorRepository.deleteRecommendLog(cancelled.getRecommendId());
            monitorRepository.insertQuizResetLog(cancelled, "CANCEL", staffName, answers, badges, cards);
        }

        // 2) 대상 책을 "문제 풀기 전"으로 되돌린다
        int answerRows = monitorRepository.deleteQuizAnswerLogs(target.getRecommendId());
        int badgeRows = monitorRepository.deleteStudentBadges(target.getStudentId(), target.getContentId());
        int cardRows = monitorRepository.deleteNormalCard(target.getStudentId(), target.getContentId());
        monitorRepository.deleteDiaryDetailByRecommend(target.getRecommendId());
        monitorRepository.resetRecommendResult(target.getRecommendId());

        // NORMAL 회수가 모두 끝난 뒤 한 번만 — 그래야 RARE 회수 조건(현재 보유 수)이 최종값으로 계산된다
        cardRows += monitorRepository.deleteOrphanRareCards(target.getStudentId());
        monitorRepository.insertQuizResetLog(target, "RESET", staffName, answerRows, badgeRows, cardRows);

        log.info("문제풀이 기록 삭제 — staff={}, studentId={}, recommendId={}, contentId={}, 이전상태={} {}/{}, "
                        + "지운 행: answer={}, badge={}, card={}, 함께 취소한 뒤 추천={}건",
                staffName, target.getStudentId(), target.getRecommendId(), target.getContentId(),
                target.getStatus(), target.getCorrectCount(), target.getTotalCount(),
                answerRows, badgeRows, cardRows, later.size());

        // 3) 뒤 추천을 취소했다면 지금 대여 중인 책(마지막 추천 책)은 반납 대상이다
        if (!later.isEmpty()) {
            bookService.returnActiveLoanByStudent(target.getStudentId());
        }

        // 4) 되돌린 책의 실물을 다시 확보한다 — 원래 판본이 없으면 같은 책의 다른 사본으로 대체하고,
        //    그마저 없으면(다른 학생이 마지막 한 권을 가져감) 확보 없이 진행한다
        MonitorRespDTO.QuizResetRespDTO resp = new MonitorRespDTO.QuizResetRespDTO();
        resp.setCancelledCount(later.size());
        Integer securedItemId = bookService.secureCopyForStudent(target.getStudentId(), target.getItemId());
        resp.setBookSecured(securedItemId != null);
        if (securedItemId != null && !securedItemId.equals(target.getItemId())) {
            // 다른 사본을 잡았으면 추천이 가리키는 판본도 실제 대여한 것으로 맞춰야, 다음에 이 학생이
            // 재입실할 때 ensureActiveLoan이 엉뚱한 판본을 다시 잡지 않는다
            monitorRepository.updateRecommendItem(target.getRecommendId(), securedItemId);
            resp.setCopySwitched(true);
            log.info("되돌린 책의 실물을 다른 사본으로 대체했습니다 — studentId={}, {} → {}",
                    target.getStudentId(), target.getItemId(), securedItemId);
        } else if (securedItemId == null) {
            log.warn("되돌린 책의 실물을 확보하지 못했습니다(다른 학생 대여 중) — studentId={}, itemId={}. "
                    + "문제풀이는 화면으로 가능하며 실물은 직원이 수동 조치해야 합니다",
                    target.getStudentId(), target.getItemId());
        }

        // 5) 기록이 사라졌으니 카드도 "독서 중"으로 돌아가야 한다 — 결과 확인중/문제 푸는 중 표시를 함께 푼다
        Integer sessionId = monitorRepository.findOpenSessionId(target.getStudentId(), KstClock.today());
        if (sessionId != null) {
            monitorRepository.clearQuizStarted(sessionId);
            monitorRepository.clearResultViewing(sessionId);
        }
        syncSafely(sessionId);

        return resp;
    }


    /**
     * 다른 화면(독서일지 등)이 같은 세션의 일지를 고친 뒤 모니터링 카드를 갱신하려고 호출한다 —
     * 저장 경로가 달라도 Firestore 미러링은 이 한 곳을 지나게 둔다.
     */
    public void syncSession(Integer sessionId) {
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

    /** 우선순위: 미입실 > 퇴실 > 문제 푸는 중 > 결과 확인중 > 재도전 필요 > 완료 > 권장시간 초과 > 독서 중(추천 직후 기본값) */
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
        // 이 상태가 우선한다(홈으로 나가면 clearResultViewing으로 해제되어 재도전 필요/완료로 넘어간다)
        if (card.getResultViewedAt() != null) {
            return "RESULT_VIEWING";
        }
        // 기본 문제풀이를 이미 한 번 제출했는데 불합격(PENDING)인 경우만 재도전 필요로 본다
        // (아직 한 번도 안 푼 경우는 correctCount가 null이라 여기 안 걸리고 READING 유지)
        if ("PENDING".equals(card.getBasicStatus()) && card.getBasicCorrectCount() != null) {
            return "RETRY_NEEDED";
        }
        // 기본 문제 합격(DONE) — 홈 화면이 "책 추천받기" 버튼을 띄우는 것과 같은 시점이다. 이 책은
        // 이미 반납 처리됐고 다음 책을 아직 안 받은 상태라, 계속 READING으로 보이면 안 된다(2026-07-29).
        if ("DONE".equals(card.getBasicStatus())) {
            return "COMPLETED";
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
                default -> { /* EXITED/COMPLETED는 별도 chip 없음 */ }
            }
            // 미입실 카드는 아직 독서일지 대상이 아니므로 미등록 카운트에서 제외
            if (card.getDiaryKey() == null && !"NOT_ENTERED".equals(card.getCardStatus())) {
                counts.setReadingLogMissing(counts.getReadingLogMissing() + 1);
            }
        }
        return counts;
    }

}
