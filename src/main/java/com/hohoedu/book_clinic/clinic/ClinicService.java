package com.hohoedu.book_clinic.clinic;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.handler.exception.Exception404;
import com.hohoedu.book_clinic.book.BookRepository;
import com.hohoedu.book_clinic.book._dto.BookRespDTO;
import com.hohoedu.book_clinic.clinic._dto.ClinicReqDTO;
import com.hohoedu.book_clinic.clinic._dto.ClinicRespDTO;
import com.hohoedu.book_clinic.monitor.MonitorService;
import com.hohoedu.book_clinic.question.QuestionRepository;
import com.hohoedu.book_clinic.question._dto.QuestionRespDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 학생 독서 클리닉(student-main 화면) 비즈니스 로직 — 1단계(책 추천)부터 재설계 (2026-07-09)
 *
 * 흐름은 로그인 모드로 분기하지 않는다: 로그인하면 무조건 책을 "확인"한다 — 이미 추천받은(미해결)
 * 책이 있으면 그 책을 그대로 다시 보여주고, 없으면 새로 추천해서 대여까지 즉시 확정한다.
 * "다른 책 추천"처럼 재추천/재대여를 유발하는 액션은 의도적으로 두지 않는다(재고가 계속 소모되는
 * 문제로 이어지므로).
 *
 * 새로 추천할 때의 규칙:
 *   1) 권장 우선순위(erp_bookstore_priority) 순으로 추천
 *   2) 현재 센터에 대여 가능한 실물 재고가 있는 책만 추천
 *   3) 직전 추천 도서와 분류·장르가 모두 같으면 제외 (dedup)
 *   4) 3의 조건에 걸려 후보가 없으면, 처음으로 돌아가 dedup 없이 우선순위 순으로 다시 추천
 *   5) 그래도 후보가 없으면(=해당 학년 책을 모두 추천받음) 한 단계 위 학년부터 순차적으로 추천
 * 추천이 확정되면 즉시 실물 재고 하나를 대여 처리하고(item_loan), 추천 이력(recommend_log)을 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicService {

    // erp_student.grade_key가 비어있는 학생(아직 학년 미등록)을 위한 안전망 기본값
    private static final String FALLBACK_SCHOOLYEAR = "05";

    // erp_bookstore_code(gubun='S')에 등록된 학년 코드 범위 — 규칙 5(윗학년 순차 추천)의 상한
    private static final int MAX_SCHOOLYEAR = 7;

    // 단계(학년)별 최고 레벨(만렙) — 각 학년이 레벨 1~12를 가진다
    private static final int MAX_LEVEL = 12;

    // student-main "이번 달에 읽은 책" 패널이 4칸 고정 레이아웃이라 서버에서도 4건으로 맞춘다
    private static final int MONTH_BOOKS_LIMIT = 4;

    // 문제풀이 합격선 = 전체 문항 수의 2/3 (12문항→8개, 15문항→10개 기준으로 확정)
    private static final double QUIZ_PASS_RATIO = 2.0 / 3;

    // 온라인 카드 N장을 모으면 오프라인 실물 카드 1장 (시스템은 진행도/달성 표시까지만)
    private static final int CARD_SET_SIZE = 10;

    // 뱃지 id (erp_bookstore_badge) — 책마다 기본 1개(1~3 택1) + 심화 1개(4~5 택1)
    private static final int BADGE_GREAT_JOB = 1;   // 참 잘했어요 (기본 첫 시도 불합격)
    private static final int BADGE_FRIEND    = 2;   // 독서친구 (기본 첫 시도 합격)
    private static final int BADGE_KING      = 3;   // 독서왕 (기본 첫 시도 만점)
    private static final int BADGE_ADV_DONE  = 4;   // 심화 완료 (심화 첫 시도 합격)
    private static final int BADGE_ADV_KING  = 5;   // 심화왕 (심화 첫 시도 만점)

    private final ClinicRepository clinicRepository;
    private final BookRepository bookRepository;
    private final QuestionRepository questionRepository;
    private final MonitorService monitorService;

    /**
     * 책 확인 — 멱등 처리. 이미 추천받은 책이 있으면 그 책 그대로 반환(재추천/재대여 없음),
     * 없으면 규칙 1~5를 순서대로 적용해 새로 고르고 즉시 대여 + 추천 이력 기록까지 처리한다.
     */
    @Transactional
    public ClinicRespDTO.RecommendBookDTO recommendBook(String studentId) {
        ClinicRespDTO.RecommendBookDTO existing = clinicRepository.findPendingRecommendBookCard(studentId);
        if (existing != null) {
            // 실시간 모니터링 기준 "입실" 시점 — 이미 추천받은 책이 있는 재로그인도 여기서 입실 처리한다
            // (오늘 이미 열린 세션이 있으면 그대로 재사용하는 멱등 로직)
            monitorService.enterSession(studentId);
            return existing;
        }
        log.info("학생 {}에게 새 추천 도서를 고릅니다", studentId);

        // 새로 추천한다는 건 이전 추천이 이미 DONE 처리됐다는 뜻(PENDING이면 위에서 그대로 반환됨) —
        // 이전 책이 아직 대여(LOANED) 상태로 남아있으면 여기서 반납 처리해 재고를 돌려준다
        BookRespDTO.ItemLoanRespDTO activeLoan = bookRepository.findActiveLoanByStudent(studentId);
        if (activeLoan != null) {
            bookRepository.updateLoanReturned(activeLoan.getLoanId());
            bookRepository.markItemReturned(activeLoan.getItemId());
            log.info("학생 {}의 이전 대여 도서를 반납 처리했습니다: loanId={}, itemId={}",
                    studentId, activeLoan.getLoanId(), activeLoan.getItemId());
        }

        String centerCode = clinicRepository.findCenterCode(studentId);
        if (centerCode == null) throw new Exception404("학생의 소속 센터를 찾을 수 없습니다: " + studentId);
        log.info("학생 {}의 소속 센터: {}", studentId, centerCode);

        String schoolyear = resolveSchoolyear(studentId);
        log.info("학생 {}의 학년 기준 코드: {}", studentId, schoolyear);
        String year = String.valueOf(Year.now().getValue());
        log.info("현재 연도: {}", year);

        Integer picked = pickWithFallback(studentId, centerCode, year, schoolyear);
        if (picked == null) throw new Exception404("추천할 수 있는 도서가 더 이상 없습니다.");
        log.info("학생 {}에게 추천할 도서 content_id: {}", studentId, picked);

        Integer itemId = bookRepository.loanAvailableItemByContent(picked, centerCode, studentId);
        if (itemId == null) throw new Exception400("대여 가능한 재고가 없습니다.");
        log.info("학생 {}에게 대여할 도서 item_id: {}", studentId, itemId);

        bookRepository.insertItemLoan(itemId, studentId);
        clinicRepository.insertRecommendLog(studentId, picked);
        log.info("학생 {}에게 추천된 도서 정보: {}", studentId, clinicRepository.findBookCard(picked));
        // 실시간 모니터링 기준 "입실" 시점 — 미입실 예약 카드가 여기서 입실로 전환된다
        monitorService.enterSession(studentId);
        return clinicRepository.findBookCard(picked);
    }

    /** 규칙 3(dedup) → 규칙 4(dedup 해제) → 규칙 5(윗학년 순차) 순서로 후보를 찾는다 */
    private Integer pickWithFallback(String studentId, String centerCode, String year, String schoolyear) {
        log.info("학생 {}에게 추천할 도서를 찾습니다: centerCode={}, year={}, schoolyear={}",
                studentId, centerCode, year, schoolyear);
        ClinicRespDTO.LastRecommendDTO last = clinicRepository.findLastRecommend(studentId);
        log.info("학생 {}의 직전 추천 도서: {}", studentId, last);
        String lastType = last == null ? null : last.getContentType();
        log.info("학생 {}의 직전 추천 도서 content_type: {}", studentId, lastType);
        String lastGenre = last == null ? null : last.getGenre();
        log.info("학생 {}의 직전 추천 도서 genre: {}", studentId, lastGenre);

        Integer picked = clinicRepository.pickNextContentId(
                studentId, centerCode, year, schoolyear, lastType, lastGenre, true);
        if (picked != null) return picked;
        log.info("학생 {}에게 추천할 도서 후보가 없습니다 — dedup 조건 해제 후 다시 시도합니다", studentId);

        // 규칙 4: 처음으로 돌아가 dedup 조건 없이 다시 추천
        picked = clinicRepository.pickNextContentId(
                studentId, centerCode, year, schoolyear, null, null, false);
        if (picked != null) return picked;
        log.info("학생 {}에게 추천할 도서 후보가 여전히 없습니다 — 한 단계 위 학년부터 순차적으로 시도합니다", studentId);

        // 규칙 5: 현재 학년 책을 모두 추천받았다면 한 단계 위 학년부터 순차적으로 시도
        int current = parseSchoolyear(schoolyear);
        for (int sy = current + 1; sy <= MAX_SCHOOLYEAR; sy++) {
            String candidate = String.format("%02d", sy);
            picked = clinicRepository.pickNextContentId(
                    studentId, centerCode, year, candidate, null, null, false);
            if (picked != null) return picked;
            log.info("학생 {}에게 추천할 도서 후보가 없습니다 — 학년 {}까지 시도했으나 모두 실패", studentId, candidate);
        }
        return null;
    }

    /**
     * 문제풀이 채점 제출 (qlevel=01 기본 / 02 심화, 생략 시 01)
     * - 정답 수는 클라이언트를 신뢰하지 않는다 — 학생이 문항별로 선택한 답안(qnum+selected)만 받아서,
     *   서버가 erp_bookstore_itempool.ans(해당 qlevel, state=S)와 직접 대조해 정답 수/총 문항 수를
     *   직접 계산한다(devtools로 correctCount를 조작해서 제출하는 것을 막기 위함, 2026-07-09).
     * - 기본/심화 모두 문항별 선택 답안을 풀이 이력(quiz_answer_log)으로 남긴다
     * - 심화(02)는 여기까지만 — 완독/등급/레벨 처리 없이 채점 결과만 반환한다
     * - 기본(01)은 합격선(전체 문항의 2/3) 이상이면 recommend_log를 DONE 처리 + 레벨 재계산(EXP 폐지, 완독 권수 기준)
     * - 합격선 미달이면 PENDING 유지(재도전 — 재로그인해도 같은 책 그대로)
     * - 정답률 100% = 독서왕(KING), 합격선 이상 100% 미만 = 독서친구(FRIEND)
     * - 이미 DONE 처리된 책을 재제출해도 기록/레벨을 다시 갱신하지 않는다
     */
    @Transactional
    public ClinicRespDTO.QuizSubmitRespDTO submitQuiz(String studentId, Integer contentId, String qlevel,
                                                        List<ClinicReqDTO.AnswerDTO> answers) {
        String resolvedQlevel = "02".equals(qlevel) ? "02" : "01";
        boolean advanced = "02".equals(resolvedQlevel);

        List<QuestionRespDTO.QuestionDTO> questions = questionRepository.searchQuestions(contentId, resolvedQlevel, null, "S");
        if (questions.isEmpty()) throw new Exception404("문제를 찾을 수 없습니다: contentId=" + contentId);

        Map<String, Integer> submittedByQnum = answers.stream()
                .collect(Collectors.toMap(ClinicReqDTO.AnswerDTO::getQnum, ClinicReqDTO.AnswerDTO::getSelected, (a, b) -> b));

        // 문항별 채점과 동시에 풀이 이력(어느 문제에 몇 번 보기를 골랐는지) 적재분을 만든다
        int totalCount = questions.size();
        int correctCount = 0;
        List<ClinicReqDTO.AnswerLogDTO> answerLogs = new ArrayList<>();
        for (QuestionRespDTO.QuestionDTO q : questions) {
            Integer selected = submittedByQnum.get(q.getQnum());
            if (selected == null) continue; // 미제출 문항은 오답 처리(이력 없음)
            boolean correct = String.valueOf(selected).equals(q.getAns());
            if (correct) correctCount++;
            ClinicReqDTO.AnswerLogDTO logRow = new ClinicReqDTO.AnswerLogDTO();
            logRow.setQnum(q.getQnum());
            logRow.setSelected(selected);
            logRow.setCorrect(correct);
            logRow.setQtype(q.getQtype());
            answerLogs.add(logRow);
        }

        ClinicRespDTO.QuizSubmitRespDTO resp = new ClinicRespDTO.QuizSubmitRespDTO();
        int passLine = (int) Math.ceil(totalCount * QUIZ_PASS_RATIO);
        resp.setCorrectCount(correctCount);
        resp.setTotalCount(totalCount);
        resp.setPassLine(passLine);

        ClinicRespDTO.RecommendLogStatusDTO logStatus = clinicRepository.findRecommendLogStatus(studentId, contentId);
        if (logStatus == null) throw new Exception404("추천 이력을 찾을 수 없습니다: studentId=" + studentId + ", contentId=" + contentId);

        // 뱃지는 "첫 시도" 결과로 등급이 정해진다(재도전 고정) — 이번 제출의 로그를 적재하기 전에
        // 같은 책+난이도의 기존 제출이 있었는지로 첫 시도 여부를 판단한다.
        boolean firstAttempt = clinicRepository.countPriorAttempts(studentId, contentId, resolvedQlevel) == 0;

        // 풀이 이력 적재 — 이미 DONE 처리된 책의 재제출이든 재도전이든, 기본/심화 모두 제출은 전부 남긴다
        if (!answerLogs.isEmpty()) {
            clinicRepository.insertQuizAnswerLogs(logStatus.getRecommendId(), studentId, contentId, resolvedQlevel, answerLogs);
        }

        // 심화문제는 완독/등급/레벨 개념이 없다 — 이력 기록/채점 결과에 더해 뱃지(심화완료/심화왕)만 판정.
        // 심화 불합격이면 뱃지 없음, 첫 시도가 아니면(재도전) 등급 변화 없음.
        if (advanced) {
            resp.setPassed(false);
            resp.setGrade(null);
            resp.setNewBadges(awardAdvancedBadge(studentId, contentId, correctCount, totalCount, passLine, firstAttempt));
            syncMonitorSafely(studentId);
            return resp;
        }

        if ("DONE".equals(logStatus.getStatus())) {
            // 이미 완독한 책의 재제출 — 첫 시도가 아니므로 새 뱃지 없음
            resp.setPassed(true);
            resp.setGrade(logStatus.getGrade());
            resp.setAlreadyCompleted(true);
            resp.setLeveledUp(false);
            resp.setNewBadges(List.of());
            syncMonitorSafely(studentId);
            return resp;
        }

        boolean passed = correctCount >= passLine;
        String grade = !passed ? null : (correctCount == totalCount ? "KING" : "FRIEND");
        String status = passed ? "DONE" : "PENDING";
        clinicRepository.updateRecommendResult(logStatus.getRecommendId(), correctCount, totalCount, grade, status);

        resp.setPassed(passed);
        resp.setGrade(grade);

        if (passed) {
            // 레벨은 EXP가 아니라 "그 학년 도서 완독 권수 ÷ 학년별 필요권수"로 정해진다(단계 = 학생 학년).
            // updateRecommendResult로 이 책이 이미 DONE 처리됐으므로 doneAfter에 이번 완독이 포함된다.
            String schoolyear = resolveSchoolyear(studentId);
            Integer booksPerLevel = clinicRepository.findBooksPerLevel(schoolyear);
            if (booksPerLevel != null && booksPerLevel > 0) {
                int doneAfter = clinicRepository.countDoneBooksByGrade(studentId, schoolyear);
                int levelBefore = levelFor(doneAfter - 1, booksPerLevel);
                int levelAfter = levelFor(doneAfter, booksPerLevel);
                resp.setLevelNo(levelAfter);
                resp.setLeveledUp(levelAfter > levelBefore);
                ClinicRespDTO.LevelDetailDTO detail = clinicRepository.findLevelDetail(schoolyear, levelAfter);
                if (detail != null) resp.setLevelTitle(detail.getTitle());
                if (levelAfter >= MAX_LEVEL) {
                    resp.setProgressPercent(100);
                    resp.setBooksToNextLevel(0);
                } else {
                    int inLevel = doneAfter % booksPerLevel;
                    resp.setProgressPercent((int) Math.round(inLevel * 100.0 / booksPerLevel));
                    resp.setBooksToNextLevel(booksPerLevel - inLevel);
                }
            }

            // 온라인 카드 — 새 완독(DONE)이므로 그 책의 카드를 1장 획득한다(책당 1장, 중복 없음).
            // 카드 10장마다 오프라인 실물 1장 교환 시점(cardRewardReached) — 실물 지급은 오프라인 수동.
            ClinicRespDTO.CardDTO card = clinicRepository.findCardByContent(contentId);
            if (card != null) {
                int totalCards = clinicRepository.countDoneBooks(studentId); // 이번 완독 포함
                resp.setCardName(card.getCardName());
                resp.setCardImageUrl(card.getImageUrl());
                resp.setTotalCards(totalCards);
                resp.setCardRewardReached(totalCards > 0 && totalCards % CARD_SET_SIZE == 0);
            }
        }

        // 기본 문제 뱃지 — 첫 시도 결과로 등급 확정(불합격→참잘했어요 / 합격→독서친구 / 만점→독서왕).
        // 재도전(첫 시도 아님)이면 등급이 고정되어 새 뱃지가 나가지 않는다.
        resp.setNewBadges(awardBasicBadge(studentId, contentId, correctCount, totalCount, passLine, firstAttempt));
        syncMonitorSafely(studentId);

        return resp;
    }

    /**
     * 문제풀이 제출로 바뀐 카드 상태(정답 수/합격 여부/뱃지 등)를 실시간 모니터링(Firestore)에
     * 반영한다. 모니터링 동기화 실패가 채점 응답에 영향을 주면 안 되므로 여기서 삼킨다.
     */
    private void syncMonitorSafely(String studentId) {
        try {
            monitorService.syncStudentToday(studentId);
        } catch (Exception e) {
            log.warn("실시간 모니터링 동기화 실패 — 채점 결과는 정상 반영됨: studentId={}", studentId, e);
        }
    }

    /**
     * student-main 화면 레벨 카드용 — 학생의 현재 레벨/진행률을 계산한다(EXP 폐지).
     * 단계 = 학생 학년(grade_key). 그 학년 도서 완독(DONE) 권수를 학년별 필요권수로 나눠 레벨(1~12)을 정하고,
     * progressPercent는 현재 레벨 구간 내 완독 비율, booksToNextLevel은 다음 레벨까지 남은 완독 권수(만렙이면 0)다.
     * 학년 규칙이 없으면(예: 미등록/중등) 레벨1·진행률0으로 취급한다.
     */
    public ClinicRespDTO.MainLevelInfoDTO getMainLevelInfo(String studentId) {
        String schoolyear = resolveSchoolyear(studentId);
        Integer booksPerLevel = clinicRepository.findBooksPerLevel(schoolyear);

        ClinicRespDTO.MainLevelInfoDTO result = new ClinicRespDTO.MainLevelInfoDTO();

        if (booksPerLevel == null || booksPerLevel <= 0) {
            result.setLevelNo(1);
            result.setProgressPercent(0);
            result.setBooksToNextLevel(null);
            return result;
        }

        int doneBooks = clinicRepository.countDoneBooksByGrade(studentId, schoolyear);
        int levelNo = levelFor(doneBooks, booksPerLevel);
        result.setLevelNo(levelNo);

        ClinicRespDTO.LevelDetailDTO detail = clinicRepository.findLevelDetail(schoolyear, levelNo);
        if (detail != null) {
            result.setLevelName(detail.getStageName());
            result.setTitle(detail.getTitle());
            result.setFeature(detail.getFeature());
        }

        if (levelNo >= MAX_LEVEL) {
            result.setProgressPercent(100);
            result.setBooksToNextLevel(0);
            return result;
        }

        int inLevel = doneBooks % booksPerLevel;
        result.setProgressPercent((int) Math.round(inLevel * 100.0 / booksPerLevel));
        result.setBooksToNextLevel(booksPerLevel - inLevel);
        return result;
    }

    /** student-main 뱃지 패널 — 학생이 획득한 뱃지 전체(이름/설명), 최근 획득순 */
    public List<ClinicRespDTO.BadgeDTO> getEarnedBadges(String studentId) {
        return clinicRepository.findEarnedBadges(studentId);
    }

    /**
     * student-main "나의 카드 컬렉션" 패널 — 완독한 책 = 보유 카드 목록(최신순)과
     * 10장당 실물 1장 교환 진행도를 계산해 내려준다(카드는 recommend_log의 DONE에서 파생).
     */
    public ClinicRespDTO.CardCollectionDTO getCardCollection(String studentId) {
        List<ClinicRespDTO.CardDTO> cards = clinicRepository.findEarnedCards(studentId);
        int total = cards.size();

        ClinicRespDTO.CardCollectionDTO result = new ClinicRespDTO.CardCollectionDTO();
        result.setCards(cards);
        result.setTotalCards(total);
        result.setExchangeableCount(total / CARD_SET_SIZE);
        result.setCardsToNextReward(CARD_SET_SIZE - total % CARD_SET_SIZE);
        return result;
    }

    /**
     * student-main "이번 달에 읽은 책" 패널 — 현재 읽는 중인 책(PENDING, 있으면 최대 1건, 항상 맨 앞) +
     * 이번 달에 합격 완료한 책(completed_at 기준)을 최신순으로, 합쳐서 최대 4건만 반환한다.
     * 패널이 4칸 고정 레이아웃이라 화면에서 다시 자르지 않도록 쿼리 단계에서 4건으로 맞춘다.
     */
    public List<ClinicRespDTO.MonthBookDTO> getMonthBooks(String studentId) {
        ClinicRespDTO.RecommendBookDTO pending = clinicRepository.findPendingRecommendBookCard(studentId);
        int completedLimit = pending == null ? MONTH_BOOKS_LIMIT : MONTH_BOOKS_LIMIT - 1;

        List<ClinicRespDTO.MonthBookDTO> result = new ArrayList<>();
        if (pending != null) {
            ClinicRespDTO.MonthBookDTO current = new ClinicRespDTO.MonthBookDTO();
            current.setContentId(pending.getContentId());
            current.setOriginalTitle(pending.getOriginalTitle());
            current.setImageUrl(pending.getImageUrl());
            current.setStatus("PENDING");
            result.add(current);
        }
        result.addAll(clinicRepository.findCompletedThisMonth(studentId, completedLimit));
        return result;
    }

    /**
     * 기본(01) 문제 뱃지 — 책마다 첫 시도 결과로 등급을 확정한다(재도전 고정).
     *   불합격 → 참 잘했어요 / 합격(만점 미만) → 독서친구 / 만점 → 독서왕
     * 첫 시도가 아니면(재도전) 등급 변화 없이 빈 목록을 반환한다.
     */
    private List<ClinicRespDTO.BadgeDTO> awardBasicBadge(String studentId, Integer contentId,
                                                         int correct, int total, int passLine, boolean firstAttempt) {
        if (!firstAttempt) return List.of();
        int badgeId = (correct >= total) ? BADGE_KING
                    : (correct >= passLine) ? BADGE_FRIEND
                    : BADGE_GREAT_JOB;
        return awardBookBadge(studentId, contentId, badgeId);
    }

    /**
     * 심화(02) 문제 뱃지 — 책마다 첫 시도 결과로 확정한다.
     *   합격(만점 미만) → 심화 완료 / 만점 → 심화왕 / 불합격 → 없음
     * 첫 시도가 아니거나 합격선 미달이면 빈 목록.
     */
    private List<ClinicRespDTO.BadgeDTO> awardAdvancedBadge(String studentId, Integer contentId,
                                                            int correct, int total, int passLine, boolean firstAttempt) {
        if (!firstAttempt || correct < passLine) return List.of();
        int badgeId = (correct >= total) ? BADGE_ADV_KING : BADGE_ADV_DONE;
        return awardBookBadge(studentId, contentId, badgeId);
    }

    /** (student, content, badge) 1건 적재 후 결과화면 팝업용 뱃지 정보를 반환 */
    private List<ClinicRespDTO.BadgeDTO> awardBookBadge(String studentId, Integer contentId, int badgeId) {
        clinicRepository.insertStudentBadge(studentId, contentId, badgeId);
        ClinicRespDTO.BadgeDTO badge = clinicRepository.findBadge(badgeId);
        log.info("학생 {} 뱃지 획득: 책 {} [{}] {}", studentId, contentId, badgeId,
                 badge == null ? "" : badge.getBadgeName());
        return badge == null ? List.of() : List.of(badge);
    }

    /** 완독 권수 → 레벨 (필요권수마다 1레벨, 1레벨부터 시작, 만렙 MAX_LEVEL로 상한) */
    private int levelFor(int doneBooks, int booksPerLevel) {
        if (doneBooks <= 0 || booksPerLevel <= 0) return 1;
        return Math.min(MAX_LEVEL, doneBooks / booksPerLevel + 1);
    }

    private int parseSchoolyear(String schoolyear) {
        try {
            return Integer.parseInt(schoolyear);
        } catch (NumberFormatException e) {
            return Integer.parseInt(FALLBACK_SCHOOLYEAR);
        }
    }

    /** 학생의 실제 학년(grade_key)을 학년 기준 로직에 쓸 S코드로 변환 — 미등록 학생은 기본값으로 대체 */
    private String resolveSchoolyear(String studentId) {
        String gradeKey = clinicRepository.findGradeKey(studentId);
        return (gradeKey == null || gradeKey.isBlank()) ? FALLBACK_SCHOOLYEAR : gradeKey;
    }
}
