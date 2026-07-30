package com.hohoedu.book_clinic.clinic;

import java.time.LocalDate;
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

    // 하루에 새로 추천받을 수 있는 책 수 상한 — 초과 시 "책 추천받기"를 막는다(2026-07-30)
    private static final int MAX_DAILY_RECOMMENDATIONS = 2;

    /** 학년(단계)별 레벨 규칙 1건 — 단계명/특징 문구/레벨업 1회당 필요 완독 권수 */
    private record LevelRule(String stageName, String feature, int booksPerLevel) {}

    // 학년별 레벨 규칙 — 어드민 편집 화면이 없어 배포로만 바뀌는 값이라 DB(구 level_rule 테이블) 대신 상수로 관리.
    // 필요권수: 초1·2=8, 초3=5, 초4~6=4
    private static final Map<String, LevelRule> LEVEL_RULES = Map.of(
            "01", new LevelRule("입문", "독서와 친해지고 즐거움을 발견하는 단계", 8),
            "02", new LevelRule("성장", "책 속 지식과 생각을 모으며 성장하는 단계", 8),
            "03", new LevelRule("탐구", "스스로 질문하고 파고들며 사고를 넓히는 단계", 5),
            "04", new LevelRule("심화", "지식을 깊이 있게 이해하고 연결하는 단계", 4),
            "05", new LevelRule("통찰", "어휘와 문해력으로 글의 본질을 읽어내는 단계", 4),
            "06", new LevelRule("마스터", "폭넓은 사고로 독서를 완성하는 단계", 4)
    );

    // student-main "이번 달에 읽은 책" 패널이 4칸 고정 레이아웃이라 서버에서도 4건으로 맞춘다
    private static final int MONTH_BOOKS_LIMIT = 4;

    // 문제풀이 합격선 = 전체 문항 수의 2/3 (12문항→8개, 15문항→10개 기준으로 확정)
    private static final double QUIZ_PASS_RATIO = 2.0 / 3;

    // 온라인 카드(NORMAL) N장마다 온라인 레어카드 1장 추가 지급 + 오프라인 실물 카드 1장(시스템은 진행도/달성 표시까지만)
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
     * 홈 화면(student-main) 진입 시 상태 조회 — 2026-07-29. 예전엔 홈 진입=자동추천이라 책을 다
     * 읽고 홈으로만 나가도 바로 다음 책이 대여 확정돼버렸다(문제도 안 풀고 퇴실하면 그 책만 붕 뜸).
     * 이제는 "입실"과 "책 추천"을 분리한다: 입실은 홈 진입 자체로 항상 확정하고, 다음 책 추천은
     * 학생이 "책 추천받기" 버튼을 눌러 POST /clinic/recommend를 호출할 때만 일어난다.
     * 단, 생애 첫 로그인(추천 이력이 아예 없음)은 보여줄 "직전 책"이 없어 애매하므로 예전처럼
     * 그 자리에서 즉시 추천한다.
     */
    @Transactional
    public ClinicRespDTO.BookStatusRespDTO getHomeState(String studentId) {
        monitorService.enterSession(studentId);

        ClinicRespDTO.RecommendBookDTO pending = clinicRepository.findPendingRecommendBookCard(studentId);
        if (pending != null) {
            // 퇴실 시 대여만 반납되고 추천(PENDING)은 그대로 남아있을 수 있다 — 재입실 시 그 책을
            // 다시 대여해 대여 상태를 되살린다(재고가 있을 때만, 2026-07-30)
            ensureActiveLoan(studentId, pending.getItemId());
            return homeState("READING", pending);
        }

        ClinicRespDTO.RecommendBookDTO lastDone = clinicRepository.findLastDoneBookCard(studentId);
        if (lastDone == null) {
            // 생애 첫 로그인 — 보여줄 직전 책이 없으니 예전처럼 바로 추천해서 내려준다
            return homeState("READING", recommendBook(studentId));
        }
        return homeState("AWAITING_NEXT", lastDone);
    }

    private ClinicRespDTO.BookStatusRespDTO homeState(String state, ClinicRespDTO.RecommendBookDTO book) {
        ClinicRespDTO.BookStatusRespDTO resp = new ClinicRespDTO.BookStatusRespDTO();
        resp.setState(state);
        resp.setBook(book);
        return resp;
    }

    /**
     * 책 확인 — 멱등 처리. 이미 추천받은 책이 있으면 그 책 그대로 반환(재추천/재대여 없음),
     * 없으면 규칙 1~5를 순서대로 적용해 새로 고르고 즉시 대여 + 추천 이력 기록까지 처리한다.
     * student-main 홈 화면에서는 더 이상 자동으로 호출하지 않고, "책 추천받기" 버튼 클릭 시에만 호출된다.
     */
    @Transactional
    public ClinicRespDTO.RecommendBookDTO recommendBook(String studentId) {
        ClinicRespDTO.RecommendBookDTO existing = clinicRepository.findPendingRecommendBookCard(studentId);
        if (existing != null) {
            // 실시간 모니터링 기준 "입실" 시점 — 이미 추천받은 책이 있는 재로그인도 여기서 입실 처리한다
            // (오늘 이미 열린 세션이 있으면 그대로 재사용하는 멱등 로직)
            monitorService.enterSession(studentId);
            // 퇴실로 대여만 반납되고 추천은 PENDING으로 남아있을 수 있다 — 재고가 있으면 다시 대여한다
            ensureActiveLoan(studentId, existing.getItemId());
            return existing;
        }

        int todayCount = clinicRepository.countTodayRecommends(studentId, LocalDate.now());
        if (todayCount >= MAX_DAILY_RECOMMENDATIONS) {
            throw new Exception400("하루에 추천받을 수 있는 책은 " + MAX_DAILY_RECOMMENDATIONS + "권을 초과할 수 없습니다.");
        }
        log.info("학생 {}에게 새 추천 도서를 고릅니다", studentId);

        // 새로 추천한다는 건 이전 추천이 이미 DONE 처리됐다는 뜻(PENDING이면 위에서 그대로 반환됨).
        // 반납은 이제 완독 확정 순간(submitQuiz)에 바로 처리되므로 보통은 여기서 할 일이 없지만,
        // 혹시 그 처리가 어떤 이유로 안 됐을 경우를 대비한 안전망으로 남겨둔다.
        returnActiveLoanSafely(studentId);

        String centerCode = clinicRepository.findCenterCode(studentId);
        if (centerCode == null) throw new Exception404("학생의 소속 센터를 찾을 수 없습니다: " + studentId);
        log.info("학생 {}의 소속 센터: {}", studentId, centerCode);

        String schoolyear = resolveSchoolyear(studentId);
        log.info("학생 {}의 학년 기준 코드: {}", studentId, schoolyear);
        String year = String.valueOf(Year.now().getValue());
        log.info("현재 연도: {}", year);

        ClinicRespDTO.PickedItemDTO picked = pickWithFallback(studentId, centerCode, year, schoolyear);
        if (picked == null) throw new Exception404("추천할 수 있는 도서가 더 이상 없습니다.");
        log.info("학생 {}에게 추천할 도서 content_id={}, item_id={}", studentId, picked.getContentId(), picked.getItemId());

        // 후보를 고른 시점과 이 시점 사이 다른 학생이 같은 item을 먼저 채갔을 수 있어 원자적으로 재확인한다
        Integer itemId = bookRepository.reserveItemById(picked.getItemId());
        if (itemId == null) throw new Exception400("대여 가능한 재고가 없습니다.");
        log.info("학생 {}에게 대여할 도서 item_id: {}", studentId, itemId);

        bookRepository.insertItemLoan(itemId, studentId);
        clinicRepository.insertRecommendLog(studentId, picked.getContentId(), itemId);
        log.info("학생 {}에게 추천된 도서 정보: {}", studentId, clinicRepository.findBookCard(picked.getContentId()));
        // 실시간 모니터링 기준 "입실" 시점 — 미입실 예약 카드가 여기서 입실로 전환된다
        monitorService.enterSession(studentId);
        ClinicRespDTO.RecommendBookDTO card = clinicRepository.findBookCard(picked.getContentId());
        card.setItemId(itemId);
        return card;
    }

    /** 규칙 3(dedup) → 규칙 4(dedup 해제) → 규칙 5(윗학년 순차) 순서로 후보를 찾는다 — item(실물 판본) 단위 선택 */
    private ClinicRespDTO.PickedItemDTO pickWithFallback(String studentId, String centerCode, String year, String schoolyear) {
        log.info("학생 {}에게 추천할 도서를 찾습니다: centerCode={}, year={}, schoolyear={}",
                studentId, centerCode, year, schoolyear);
        ClinicRespDTO.LastRecommendDTO last = clinicRepository.findLastRecommend(studentId);
        log.info("학생 {}의 직전 추천 도서: {}", studentId, last);
        String lastType = last == null ? null : last.getContentType();
        log.info("학생 {}의 직전 추천 도서 content_type: {}", studentId, lastType);
        String lastGenre = last == null ? null : last.getGenre();
        log.info("학생 {}의 직전 추천 도서 genre: {}", studentId, lastGenre);

        ClinicRespDTO.PickedItemDTO picked = clinicRepository.pickNextItem(
                studentId, centerCode, year, schoolyear, lastType, lastGenre, true);
        if (picked != null) return picked;
        log.info("학생 {}에게 추천할 도서 후보가 없습니다 — dedup 조건 해제 후 다시 시도합니다", studentId);

        // 규칙 4: 처음으로 돌아가 dedup 조건 없이 다시 추천
        picked = clinicRepository.pickNextItem(
                studentId, centerCode, year, schoolyear, null, null, false);
        if (picked != null) return picked;
        log.info("학생 {}에게 추천할 도서 후보가 여전히 없습니다 — 한 단계 위 학년부터 순차적으로 시도합니다", studentId);

        // 규칙 5: 현재 학년 책을 모두 추천받았다면 한 단계 위 학년부터 순차적으로 시도
        int current = parseSchoolyear(schoolyear);
        for (int sy = current + 1; sy <= MAX_SCHOOLYEAR; sy++) {
            String candidate = String.format("%02d", sy);
            picked = clinicRepository.pickNextItem(
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
            recordDiarySafely(studentId, contentId, logStatus.getRecommendId(), resolvedQlevel, correctCount, totalCount);
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
            recordDiarySafely(studentId, contentId, logStatus.getRecommendId(), resolvedQlevel, correctCount, totalCount);
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
            // 완독 확정 순간 바로 반납 처리한다(2026-07-29) — 예전엔 "다음 책 추천" 시점까지 반납을
            // 미뤄서, 학생이 결과 화면에서 다음 책을 안 받고 그냥 홈/퇴실해버리면 대여 상태가 그대로
            // 남아 다른 학생이 그 책을 못 빌렸다. 다음 책을 받든 안 받든 이 책은 이제 필요 없으므로
            // 여기서 즉시 재고를 돌려준다.
            returnActiveLoanSafely(studentId);

            // 레벨은 EXP가 아니라 "그 학년 도서 완독 권수 ÷ 학년별 필요권수"로 정해진다(단계 = 학생 학년).
            // updateRecommendResult로 이 책이 이미 DONE 처리됐으므로 doneAfter에 이번 완독이 포함된다.
            String schoolyear = resolveSchoolyear(studentId);
            LevelRule rule = LEVEL_RULES.get(schoolyear);
            if (rule != null) {
                int booksPerLevel = rule.booksPerLevel();
                int doneAfter = clinicRepository.countDoneBooksByGrade(studentId, schoolyear);
                int levelBefore = levelFor(doneAfter - 1, booksPerLevel);
                int levelAfter = levelFor(doneAfter, booksPerLevel);
                resp.setLevelNo(levelAfter);
                resp.setLeveledUp(levelAfter > levelBefore);
                resp.setLevelTitle(clinicRepository.findLevelTitle(schoolyear, levelAfter));
                if (levelAfter >= MAX_LEVEL) {
                    resp.setProgressPercent(100);
                    resp.setBooksToNextLevel(0);
                } else {
                    int inLevel = doneAfter % booksPerLevel;
                    resp.setProgressPercent((int) Math.round(inLevel * 100.0 / booksPerLevel));
                    resp.setBooksToNextLevel(booksPerLevel - inLevel);
                }
            }

            // 온라인 카드 — 새 완독(DONE)이므로 그 책의 NORMAL 카드를 1장 지급한다(책당 1장, 중복 지급 방지).
            // NORMAL 카드가 CARD_SET_SIZE(10)의 배수를 채운 순간 온라인 레어카드도 함께 지급하고,
            // 그 시점을 cardRewardReached로 알려 오프라인 실물 1장 교환 안내를 띄운다(실물 지급은 오프라인 수동).
            if (!clinicRepository.existsNormalCard(studentId, contentId)) {
                clinicRepository.insertNormalCard(studentId, contentId);
                ClinicRespDTO.CardDTO card = clinicRepository.findCardByContent(contentId);
                int totalCards = clinicRepository.countNormalCards(studentId); // 이번 완독 포함
                if (card != null) {
                    resp.setCardName(card.getCardName());
                    resp.setCardImageUrl(card.getImageUrl());
                }
                resp.setTotalCards(totalCards);

                boolean rewardReached = totalCards > 0 && totalCards % CARD_SET_SIZE == 0;
                resp.setCardRewardReached(rewardReached);
                if (rewardReached && !clinicRepository.existsRareCard(studentId, totalCards)) {
                    clinicRepository.insertRareCard(studentId, totalCards);
                }
            }
        }

        // 기본 문제 뱃지 — 첫 시도 결과로 등급 확정(불합격→참잘했어요 / 합격→독서친구 / 만점→독서왕).
        // 재도전(첫 시도 아님)이면 등급이 고정되어 새 뱃지가 나가지 않는다.
        resp.setNewBadges(awardBasicBadge(studentId, contentId, correctCount, totalCount, passLine, firstAttempt));
        recordDiarySafely(studentId, contentId, logStatus.getRecommendId(), resolvedQlevel, correctCount, totalCount);
        syncMonitorSafely(studentId);

        return resp;
    }

    /**
     * PENDING(아직 안 끝난) 추천 도서인데 현재 대여 이력이 없으면 다시 대여한다 — 퇴실 처리
     * (MonitorService.exitSession)가 그 순간 대여를 반납해버리므로, 같은 책을 계속 읽는 중인
     * 학생이 재입실하면 대여 상태를 되살려줘야 한다(2026-07-30). 추천이 item(실물 판본) 단위라
     * 재입실 시에도 원래 추천받았던 그 정확한 item을 다시 대여해야 한다 — 같은 content의 다른
     * item(다른 학생이 읽는 중일 수 있음)을 대신 잡으면 recommend_log.item_id와 실제 대여가
     * 어긋난다. 그 item의 재고가 없으면(분실 등) 대여 이력 없이도 읽던 책은 그대로 보여준다.
     */
    private void ensureActiveLoan(String studentId, Integer itemId) {
        if (bookRepository.findActiveLoanByStudent(studentId) != null) return;

        Integer reservedItemId = bookRepository.reserveItemById(itemId);
        if (reservedItemId == null) {
            log.warn("재입실 재대여 실패 — 대여 가능한 재고 없음: studentId={}, itemId={}", studentId, itemId);
            return;
        }
        bookRepository.insertItemLoan(reservedItemId, studentId);
        log.info("학생 {}의 읽던 책을 재입실 시점에 재대여했습니다: itemId={}", studentId, reservedItemId);
    }

    /** 완독 확정 시점에 현재 대여 중인 도서를 즉시 반납 처리한다(없으면 조용히 넘어감) */
    private void returnActiveLoanSafely(String studentId) {
        BookRespDTO.ItemLoanRespDTO activeLoan = bookRepository.findActiveLoanByStudent(studentId);
        if (activeLoan == null) return;
        bookRepository.updateLoanReturned(activeLoan.getLoanId());
        bookRepository.markItemReturned(activeLoan.getItemId());
        log.info("학생 {}의 완독 도서를 반납 처리했습니다: loanId={}, itemId={}",
                studentId, activeLoan.getLoanId(), activeLoan.getItemId());
    }

    /**
     * 독서일지 상세 자동 적재 — 읽은 책/독서 시간/채점 결과는 직원 입력이 아니라 제출 시점에 시스템이 남긴다.
     * 일지 적재가 실패해도 채점 결과 자체는 이미 확정된 뒤이므로 제출을 되돌리지 않는다.
     */
    private void recordDiarySafely(String studentId, Integer contentId, Integer recommendId,
                                   String qlevel, int correctCount, int totalCount) {
        try {
            monitorService.recordDiaryDetail(studentId, contentId, recommendId, qlevel, correctCount, totalCount);
        } catch (Exception e) {
            log.warn("독서일지 상세 적재 실패 — studentId={}, contentId={}", studentId, contentId, e);
        }
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
        LevelRule rule = LEVEL_RULES.get(schoolyear);

        ClinicRespDTO.MainLevelInfoDTO result = new ClinicRespDTO.MainLevelInfoDTO();

        if (rule == null) {
            result.setLevelNo(1);
            result.setProgressPercent(0);
            result.setBooksToNextLevel(null);
            return result;
        }

        int booksPerLevel = rule.booksPerLevel();
        int doneBooks = clinicRepository.countDoneBooksByGrade(studentId, schoolyear);
        int levelNo = levelFor(doneBooks, booksPerLevel);
        result.setLevelNo(levelNo);
        result.setLevelName(rule.stageName());
        result.setFeature(rule.feature());
        result.setTitle(clinicRepository.findLevelTitle(schoolyear, levelNo));

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
     * student-main "나의 카드 컬렉션" 패널 — 보유 카드(NORMAL+RARE) 목록(최신 획득순)과
     * NORMAL 카드 기준 10장당 레어카드/실물 교환 진행도를 내려준다(erp_bookstore_student_card 조회).
     */
    public ClinicRespDTO.CardCollectionDTO getCardCollection(String studentId) {
        List<ClinicRespDTO.CardDTO> cards = clinicRepository.findEarnedCards(studentId);
        int normalTotal = clinicRepository.countNormalCards(studentId);

        ClinicRespDTO.CardCollectionDTO result = new ClinicRespDTO.CardCollectionDTO();
        result.setCards(cards);
        result.setTotalCards(normalTotal);
        result.setExchangeableCount(normalTotal / CARD_SET_SIZE);
        result.setCardsToNextReward(CARD_SET_SIZE - normalTotal % CARD_SET_SIZE);
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
