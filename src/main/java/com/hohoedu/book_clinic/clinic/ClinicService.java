package com.hohoedu.book_clinic.clinic;

import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.handler.exception.Exception404;
import com.hohoedu.book_clinic.book.BookRepository;
import com.hohoedu.book_clinic.book._dto.BookRespDTO;
import com.hohoedu.book_clinic.clinic._dto.ClinicReqDTO;
import com.hohoedu.book_clinic.clinic._dto.ClinicRespDTO;
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

    // erp_bookstore_level에 등록된 최고 레벨(만렙) — 1~30, 11~20 성장, 21~30 마스터
    private static final int MAX_LEVEL = 30;

    // student-main "이번 달에 읽은 책" 패널이 4칸 고정 레이아웃이라 서버에서도 4건으로 맞춘다
    private static final int MONTH_BOOKS_LIMIT = 4;

    // 문제풀이 합격선 = 전체 문항 수의 2/3 (12문항→8개, 15문항→10개 기준으로 확정)
    private static final double QUIZ_PASS_RATIO = 2.0 / 3;

    // MONTH_STREAK 뱃지 판정용 월간 완독 목표 권수 — 1·2학년은 8권, 그 외 학년은 4권 (완독 = 문제풀이 합격)
    private static final int MONTH_QUOTA_LOW_GRADE = 8;
    private static final int MONTH_QUOTA_DEFAULT = 4;

    private final ClinicRepository clinicRepository;
    private final BookRepository bookRepository;
    private final QuestionRepository questionRepository;

    /**
     * 책 확인 — 멱등 처리. 이미 추천받은 책이 있으면 그 책 그대로 반환(재추천/재대여 없음),
     * 없으면 규칙 1~5를 순서대로 적용해 새로 고르고 즉시 대여 + 추천 이력 기록까지 처리한다.
     */
    @Transactional
    public ClinicRespDTO.RecommendBookDTO recommendBook(String studentId) {
        ClinicRespDTO.RecommendBookDTO existing = clinicRepository.findPendingRecommendBookCard(studentId);
        if (existing != null) return existing;
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
     * - 심화(02)는 여기까지만 — 완독/EXP/등급 처리 없이 채점 결과만 반환한다
     * - 기본(01)은 합격선(전체 문항의 2/3) 이상이면 recommend_log를 DONE 처리 + EXP 적립 + 레벨 재계산
     * - 합격선 미달이면 PENDING 유지(재도전 — 재로그인해도 같은 책 그대로)
     * - 정답률 100% = 독서왕(KING), 합격선 이상 100% 미만 = 독서친구(FRIEND)
     * - 이미 DONE 처리된 책을 재제출해도 기록/EXP를 다시 갱신하지 않는다
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

        // 풀이 이력 적재 — 이미 DONE 처리된 책의 재제출(EXP 없음)이든 재도전이든, 기본/심화 모두 제출은 전부 남긴다
        if (!answerLogs.isEmpty()) {
            clinicRepository.insertQuizAnswerLogs(logStatus.getRecommendId(), studentId, contentId, resolvedQlevel, answerLogs);
        }

        // 심화문제는 완독/EXP/등급 개념이 없다 — 이력 기록/채점 결과에 더해 뱃지 판정만 수행
        // (심화 만점 뱃지가 여기서 열리고, 과거 이력 소급 획득분도 함께 나갈 수 있다)
        if (advanced) {
            resp.setPassed(false);
            resp.setGrade(null);
            resp.setNewBadges(checkAndAwardBadges(studentId));
            return resp;
        }

        if ("DONE".equals(logStatus.getStatus())) {
            resp.setPassed(true);
            resp.setGrade(logStatus.getGrade());
            resp.setAlreadyCompleted(true);
            resp.setExpGained(0);
            resp.setLeveledUp(false);
            resp.setNewBadges(checkAndAwardBadges(studentId));
            return resp;
        }

        boolean passed = correctCount >= passLine;
        String grade = !passed ? null : (correctCount == totalCount ? "KING" : "FRIEND");
        String status = passed ? "DONE" : "PENDING";
        clinicRepository.updateRecommendResult(logStatus.getRecommendId(), correctCount, totalCount, grade, status);

        resp.setPassed(passed);
        resp.setGrade(grade);

        if (passed) {
            String bookSchoolyear = clinicRepository.findContentSchoolyear(contentId);
            Integer expPerBook = bookSchoolyear == null ? null : clinicRepository.findExpPerBook(bookSchoolyear);
            int expGained = expPerBook == null ? 0 : expPerBook;

            ClinicRespDTO.StudentExpDTO before = clinicRepository.findStudentExp(studentId);
            int currentExp = before == null ? 0 : before.getExp();
            int currentLevel = before == null ? 1 : before.getLevelNo();

            int newExp = currentExp + expGained;
            int newLevel = calculateLevel(newExp);
            clinicRepository.upsertStudentExp(studentId, expGained, newLevel);

            resp.setExpGained(expGained);
            resp.setLevelNo(newLevel);
            resp.setLeveledUp(newLevel > currentLevel);
        }

        // 합격/불합격과 무관하게 매 제출마다 뱃지 판정 — 지표는 로그에서 재계산하므로
        // 뱃지 오픈 이전의 과거 이력도 이 시점에 자동으로 소급 획득된다
        resp.setNewBadges(checkAndAwardBadges(studentId));

        return resp;
    }

    /**
     * student-main 화면 레벨 카드용 — 학생의 현재 레벨/EXP 진행률을 계산한다.
     * student_info 행이 없으면(아직 한 번도 합격 못 한 학생) 레벨1/EXP0 취급.
     * progressPercent는 "현재 레벨 구간"(이전 레벨 required_exp ~ 현재 레벨 required_exp) 안에서의 비율,
     * booksToNextLevel은 남은 EXP를 학생 학년의 권당 EXP로 환산한 예상치(만렙이면 0)다.
     */
    public ClinicRespDTO.MainLevelInfoDTO getMainLevelInfo(String studentId) {
        ClinicRespDTO.StudentExpDTO studentExp = clinicRepository.findStudentExp(studentId);
        int exp = studentExp == null ? 0 : studentExp.getExp();
        int levelNo = studentExp == null ? 1 : studentExp.getLevelNo();

        ClinicRespDTO.LevelDetailDTO current = clinicRepository.findLevelDetail(levelNo);

        ClinicRespDTO.MainLevelInfoDTO result = new ClinicRespDTO.MainLevelInfoDTO();
        result.setLevelNo(levelNo);
        result.setLevelName(current.getLevelName());
        result.setFeature(current.getFeature());

        if (levelNo >= MAX_LEVEL) {
            result.setProgressPercent(100);
            result.setBooksToNextLevel(0);
            return result;
        }

        int lowerBound = levelNo <= 1 ? 0 : clinicRepository.findLevelDetail(levelNo - 1).getRequiredExp();
        int upperBound = current.getRequiredExp();
        int span = Math.max(1, upperBound - lowerBound);
        int progressPercent = (int) Math.min(100, Math.round((exp - lowerBound) * 100.0 / span));
        result.setProgressPercent(Math.max(0, progressPercent));

        int remainingExp = Math.max(0, upperBound - exp);
        String schoolyear = resolveSchoolyear(studentId);
        Integer expPerBook = clinicRepository.findExpPerBook(schoolyear);
        result.setBooksToNextLevel(expPerBook == null || expPerBook <= 0
                ? null : (int) Math.ceil(remainingExp / (double) expPerBook));
        return result;
    }

    /** student-main 뱃지 패널 — 학생이 획득한 뱃지 전체(이름/설명), 최근 획득순 */
    public List<ClinicRespDTO.BadgeDTO> getEarnedBadges(String studentId) {
        return clinicRepository.findEarnedBadges(studentId);
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
     * 뱃지 판정 — 매 제출마다 로그(recommend_log/quiz_answer_log)에서 지표를 재계산해서,
     * 조건을 새로 충족한 뱃지만 획득 처리하고 그 목록을 반환한다 (결과 화면 팝업용).
     * 카운터를 따로 쌓지 않으므로 과거 이력 소급 적용이 자동으로 이뤄지고(다음 제출 시점),
     * (student_id, badge_id) PK + 보유 목록 선확인으로 중복 획득이 없다.
     */
    private List<ClinicRespDTO.BadgeDTO> checkAndAwardBadges(String studentId) {
        List<ClinicRespDTO.BadgeDTO> allBadges = clinicRepository.findAllBadges();
        Set<Integer> earned = new HashSet<>(clinicRepository.findEarnedBadgeIds(studentId));
        if (earned.size() >= allBadges.size()) return List.of(); // 전부 보유 — 더 볼 것 없음

        int doneBooks = clinicRepository.countDoneBooks(studentId);
        int monthlyQuota = resolveMonthlyQuota(studentId);
        List<String> qualifiedMonths = clinicRepository.findMonthlyDoneCounts(studentId).stream()
                .filter(m -> m.getCount() >= monthlyQuota)
                .map(ClinicRespDTO.MonthCompletionDTO::getYearMonth)
                .toList();
        int maxMonthStreak = calcMaxMonthStreak(qualifiedMonths);
        int kingCount = clinicRepository.countKingGrades(studentId);
        int advPerfect = clinicRepository.countAdvancedPerfectBooks(studentId);
        Map<String, Integer> perfectByQtype = clinicRepository.countQtypePerfectBooks(studentId).stream()
                .collect(Collectors.toMap(ClinicRespDTO.QtypePerfectDTO::getQtype,
                                          ClinicRespDTO.QtypePerfectDTO::getCnt));

        List<ClinicRespDTO.BadgeDTO> newlyEarned = new ArrayList<>();
        ClinicRespDTO.BadgeDTO metaBadge = null; // META(모든 업적)는 나머지 판정이 끝난 뒤 마지막에 확인
        for (ClinicRespDTO.BadgeDTO badge : allBadges) {
            if (earned.contains(badge.getBadgeId())) continue;
            if ("META".equals(badge.getCategory())) { metaBadge = badge; continue; }

            boolean achieved = switch (badge.getCategory()) {
                case "FIRST_BOOK" -> doneBooks >= badge.getThreshold();
                case "MONTH_STREAK" -> maxMonthStreak >= badge.getThreshold();
                case "CROWN" -> kingCount >= badge.getThreshold();
                case "QTYPE_PERFECT" -> perfectByQtype.getOrDefault(badge.getParam(), 0) >= badge.getThreshold();
                case "TOPIC" -> clinicRepository.countTopicBooks(studentId,
                        Arrays.stream(badge.getParam().split(",")).map(String::trim).toList()) >= badge.getThreshold();
                case "ADV_PERFECT" -> advPerfect >= badge.getThreshold();
                default -> false;
            };
            if (achieved) {
                clinicRepository.insertStudentBadge(studentId, badge.getBadgeId());
                earned.add(badge.getBadgeId());
                newlyEarned.add(badge);
                log.info("학생 {} 뱃지 획득: [{}] {}", studentId, badge.getBadgeId(), badge.getBadgeName());
            }
        }

        if (metaBadge != null && earned.size() >= metaBadge.getThreshold()) {
            clinicRepository.insertStudentBadge(studentId, metaBadge.getBadgeId());
            newlyEarned.add(metaBadge);
            log.info("학생 {} 뱃지 획득: [{}] {} (모든 업적 달성)", studentId, metaBadge.getBadgeId(), metaBadge.getBadgeName());
        }
        return newlyEarned;
    }

    /** 학생 학년 기준 월간 완독 목표 권수 — 1·2학년은 8권, 그 외 학년은 4권 */
    private int resolveMonthlyQuota(String studentId) {
        String schoolyear = resolveSchoolyear(studentId);
        return ("01".equals(schoolyear) || "02".equals(schoolyear)) ? MONTH_QUOTA_LOW_GRADE : MONTH_QUOTA_DEFAULT;
    }

    /**
     * 역대 최장 "연속 월간 목표 달성 개월 수" — 그 달의 완독 권수가 학년별 월간 목표(resolveMonthlyQuota)
     * 이상인 달('yyyy-MM' 오름차순 목록)만 모아, 달력상 연달아 이어진 가장 긴 구간의 길이를 구한다.
     * 목표 미달 달은 완전히 제외되므로 그 달에서 스트릭이 끊기고 다음 목표 달성 달부터 다시 1로 시작한다.
     * 업적 뱃지라 한 번 찍은 최장 기록은 이후 공백이 생겨도 유지된다.
     */
    private int calcMaxMonthStreak(List<String> sortedMonths) {
        int best = 0, run = 0;
        YearMonth prev = null;
        for (String m : sortedMonths) {
            YearMonth ym = YearMonth.parse(m);
            run = (prev != null && prev.plusMonths(1).equals(ym)) ? run + 1 : 1;
            best = Math.max(best, run);
            prev = ym;
        }
        return best;
    }

    /** 누적 EXP로부터 현재 레벨을 재계산 (required_exp는 그 레벨에서 다음 레벨로 가는 데 필요한 누적 EXP 기준치) */
    private int calculateLevel(int exp) {
        int level = 1;
        for (ClinicRespDTO.LevelDTO l : clinicRepository.findAllLevels()) {
            if (l.getLevelNo() >= MAX_LEVEL) break;
            if (exp >= l.getRequiredExp()) level = l.getLevelNo() + 1;
            else break;
        }
        return level;
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
