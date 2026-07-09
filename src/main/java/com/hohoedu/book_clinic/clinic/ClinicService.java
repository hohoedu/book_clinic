package com.hohoedu.book_clinic.clinic;

import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.handler.exception.Exception404;
import com.hohoedu.book_clinic.book.BookRepository;
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

    // 문제풀이 합격선 = 전체 문항 수의 2/3 (12문항→8개, 15문항→10개 기준으로 확정)
    private static final double QUIZ_PASS_RATIO = 2.0 / 3;

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
        String bcode = clinicRepository.pickAvailableBcode(picked, centerCode);
        if (bcode == null) throw new Exception404("대여 가능한 실물 도서가 없습니다.");
        log.info("학생 {}에게 대여할 도서 bcode: {}", studentId, bcode);

        int updated = bookRepository.incrementLoanedQty(bcode, centerCode);
        if (updated == 0) throw new Exception400("대여 가능한 재고가 없습니다.");
        log.info("학생 {}에게 대여할 도서 재고 업데이트: {}", studentId, updated);
        
        bookRepository.insertItemLoan(bcode, centerCode, studentId);
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
     * 기본 문제풀이(qlevel=01) 채점 제출
     * - 정답 수는 클라이언트를 신뢰하지 않는다 — 학생이 문항별로 선택한 답안(qnum+selected)만 받아서,
     *   서버가 erp_bookstore_itempool.ans(qlevel=01, state=S)와 직접 대조해 정답 수/총 문항 수를
     *   직접 계산한다(devtools로 correctCount를 조작해서 제출하는 것을 막기 위함, 2026-07-09).
     * - 합격선(전체 문항의 2/3) 이상이면 recommend_log를 DONE 처리 + EXP 적립 + 레벨 재계산
     * - 합격선 미달이면 PENDING 유지(재도전 — 재로그인해도 같은 책 그대로)
     * - 정답률 100% = 독서왕(KING), 합격선 이상 100% 미만 = 독서친구(FRIEND)
     * - 이미 DONE 처리된 책을 재제출해도 기록/EXP를 다시 갱신하지 않는다
     */
    @Transactional
    public ClinicRespDTO.QuizSubmitRespDTO submitQuiz(String studentId, Integer contentId,
                                                        List<ClinicReqDTO.AnswerDTO> answers) {
        List<QuestionRespDTO.QuestionDTO> questions = questionRepository.searchQuestions(contentId, "01", null, "S");
        if (questions.isEmpty()) throw new Exception404("문제를 찾을 수 없습니다: contentId=" + contentId);

        Map<String, String> correctAnswerByQnum = questions.stream()
                .collect(Collectors.toMap(QuestionRespDTO.QuestionDTO::getQnum, QuestionRespDTO.QuestionDTO::getAns));
        Map<String, Integer> submittedByQnum = answers.stream()
                .collect(Collectors.toMap(ClinicReqDTO.AnswerDTO::getQnum, ClinicReqDTO.AnswerDTO::getSelected, (a, b) -> b));

        int totalCount = correctAnswerByQnum.size();
        int correctCount = 0;
        for (Map.Entry<String, String> e : correctAnswerByQnum.entrySet()) {
            Integer selected = submittedByQnum.get(e.getKey());
            if (selected != null && String.valueOf(selected).equals(e.getValue())) correctCount++;
        }

        ClinicRespDTO.QuizSubmitRespDTO resp = new ClinicRespDTO.QuizSubmitRespDTO();
        int passLine = (int) Math.ceil(totalCount * QUIZ_PASS_RATIO);
        resp.setCorrectCount(correctCount);
        resp.setTotalCount(totalCount);
        resp.setPassLine(passLine);

        ClinicRespDTO.RecommendLogStatusDTO logStatus = clinicRepository.findRecommendLogStatus(studentId, contentId);
        if (logStatus == null) throw new Exception404("추천 이력을 찾을 수 없습니다: studentId=" + studentId + ", contentId=" + contentId);

        if ("DONE".equals(logStatus.getStatus())) {
            resp.setPassed(true);
            resp.setGrade(logStatus.getGrade());
            resp.setAlreadyCompleted(true);
            resp.setExpGained(0);
            resp.setLeveledUp(false);
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

        return resp;
    }

    /** 누적 EXP로부터 현재 레벨을 재계산 (required_exp는 그 레벨에서 다음 레벨로 가는 데 필요한 누적 EXP 기준치) */
    private int calculateLevel(int exp) {
        int level = 1;
        for (ClinicRespDTO.LevelDTO l : clinicRepository.findAllLevels()) {
            if (l.getLevelNo() >= 10) break;
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
