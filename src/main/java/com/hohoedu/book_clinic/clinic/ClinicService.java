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
import com.hohoedu.book_clinic._core.utils.KstClock;
import com.hohoedu.book_clinic.book.BookRepository;
import com.hohoedu.book_clinic.book._dto.BookRespDTO;
import com.hohoedu.book_clinic.clinic._dto.ClinicReqDTO;
import com.hohoedu.book_clinic.clinic._dto.ClinicRespDTO;
import com.hohoedu.book_clinic.monitor.MonitorService;
import com.hohoedu.book_clinic.question.QuestionRepository;
import com.hohoedu.book_clinic.reservation.ReservationService;
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
 *   1) 권장 우선순위(erp_bookstore_priority) 순으로 추천 — "어떤 순서로 읽히느냐"는 이 코드가 아니라
 *      순위표가 정한다. 순위표는 학년별로 나뉘고, 그 안에서 난이도 하→중→상, 같은 난이도 안에서는
 *      분류가 연달아 나오지 않게 섞은 순서다(정렬 규칙은 db/data.sql의 priority 시드 주석 참고).
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
    private static final String FALLBACK_SCHOOLYEAR = "01";

    // erp_bookstore_code(gubun='S')에 등록된 학년 코드 범위 — 규칙 5(윗학년 순차 추천)의 상한
    private static final int MAX_SCHOOLYEAR = 7;

    /**
     * erp_student.grade_key는 book_clinic 자체 코드(01~07)가 아니라 올패스(외부 학생 마스터) 학년
     * 코드가 그대로 들어온다(2026-08-07 확인 — 예: grade_key='13'인 학생이 실제로는 초3인데,
     * schoolyear=13으로 취급되면서 priority_draft(01~07만 존재)에서 아무 것도 못 찾고 상위학년
     * 확장 루프(current+1..MAX_SCHOOLYEAR)도 13이 이미 7을 넘어 한 번도 안 돌아 추천이 통째로
     * 실패했다). erp_student는 올패스와 공유하는 테이블이라 grade_key 값 자체를 book_clinic
     * 코드로 덮어쓸 수 없고(올패스 쪽이 자기 코드로 읽어야 한다), 이 맵은 grade_key →
     * clinic_grade_key(아래 참고)를 최초 1회 채울 때만 쓴다. 유치원(05/06/07=5·6·7세)은
     * "어떻게 될지 몰라도 일단 추천은 받게 해달라"는 요청으로 초1 수준(FALLBACK_SCHOOLYEAR)에
     * 매핑했다 — 매핑 없이 그대로 넘기면 하필 book_clinic 자체 코드의 초5/초6/중등과 숫자가
     * 겹쳐 엉뚱한 학년으로 추천될 뻔했다.
     */
    private static final Map<String, String> OLPASS_GRADE_TO_SCHOOLYEAR = Map.ofEntries(
            Map.entry("05", "01"),   // 유치원 5세 → 일단 초1 수준부터
            Map.entry("06", "01"),   // 유치원 6세
            Map.entry("07", "01"),   // 유치원 7세
            Map.entry("11", "01"),   // 초1
            Map.entry("12", "02"),   // 초2
            Map.entry("13", "03"),   // 초3
            Map.entry("14", "04"),   // 초4
            Map.entry("15", "05"),   // 초5
            Map.entry("16", "06"),   // 초6
            Map.entry("21", "07"),   // 중1
            Map.entry("22", "07"),   // 중2
            Map.entry("23", "07")    // 중3
    );

    // 단계(학년)별 최고 레벨(만렙) — 각 학년이 레벨 1~12를 가진다
    private static final int MAX_LEVEL = 12;

    // 회차(타임)당 새로 추천받을 수 있는 책 수 상한(2026-08-28, 기존 "하루 2권" → "회차당 2권").
    // 실제 상한 = 이 값 × 오늘 출석(ATTENDED) 회차 수. 하루 최대 4회차까지 예약 가능하므로 최대 8권.
    // recommend_log가 회차와 연결돼 있지 않아 "회차별로 정확히 2권씩"이 아니라 그날 총량으로만 제한한다.
    private static final int MAX_RECOMMENDATIONS_PER_SLOT = 2;

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

    // "독서탐험" 지도 전체 칸 수 = 학년별 연간 목표 완독 권수(2026-08-25, 사용자 확정값).
    // 07(중등)은 별도 확정 전이라 06과 동일한 값을 임시로 쓴다.
    private static final Map<String, Integer> STEP_TOTAL_BY_SCHOOLYEAR = Map.of(
            "01", 96,
            "02", 96,
            "03", 60,
            "04", 50,
            "05", 50,
            "06", 50,
            "07", 50
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
    private final ReservationService reservationService;

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
     * 문제풀이 기기(학생 개인 폰 앱 로그인) 홈 화면 전용 상태 조회 — 입실 처리도, 책 추천도 하지 않는다.
     * 입실(enterSession)과 다음 책 추천(recommendBook)은 출석체크 기기(/attendance/enter →
     * getHomeState) 또는 이 화면의 "책 추천받기" 버튼(POST /clinic/recommend)에서만 일어난다.
     * 오늘 입실했고 아직 퇴실 전이면(로그인 단계에서 이미 보장됨) 아래 순서로 보여준다(2026-08-25):
     *   1) PENDING 추천(읽는 중인 책)이 있으면 그 책 — READING
     *   2) 없지만 가장 최근에 끝낸(DONE) 책이 있으면 그 책 — COMPLETED (완료 화면으로 안내)
     *   3) 추천 이력이 아예 없으면(정상 흐름에선 거의 없음) 안내 메시지 — NOT_ENTERED
     */
    public ClinicRespDTO.BookStatusRespDTO getQuizHomeState(String studentId) {
        if (monitorService.hasExitedToday(studentId)) {
            return homeState("EXITED", null);
        }
        ClinicRespDTO.RecommendBookDTO pending = clinicRepository.findPendingRecommendBookCard(studentId);
        if (pending != null) {
            // 퇴실 시 대여만 반납되고 추천(PENDING)은 그대로 남아있을 수 있다 — 재입실로 대여 상태가
            // 이미 복구됐겠지만, 문제풀이 화면에서도 안전하게 한 번 더 확인한다.
            ensureActiveLoan(studentId, pending.getItemId());
            return homeState("READING", pending);
        }
        ClinicRespDTO.RecommendBookDTO lastDone = clinicRepository.findLastDoneBookCard(studentId);
        if (lastDone != null) {
            return homeState("COMPLETED", lastDone);
        }
        return homeState("NOT_ENTERED", null);
    }

    /**
     * 문제풀이 화면 진입 시 모드 판별 — "나가기"를 눌렀을 때 어디로 보낼지 결정하는 데 쓴다(2026-08-25).
     *   FIRST       — 이 책+qlevel 첫 시도. 나가면 그냥 홈으로.
     *   RETRY       — 불합격(PENDING) 후 재도전 중. 나가면 직전 불합격 결과 화면으로.
     *   COMPLETION  — 이미 합격(DONE)한 책의 "틀린 문제만 다시 풀기" 중이거나, 심화(qlevel=02) 문제를
     *                 푸는 중. 나가면 홈의 완료 화면으로. 심화는 기본 문제가 이미 DONE 처리된 책에
     *                 대한 도전이라 PENDING 추천이 없으므로, 처음 도전이어도 일반 홈이 아니라 항상
     *                 완료 화면으로 보내야 한다(2026-08-25) — 안 그러면 일반 홈이 PENDING을 못 찾고
     *                 "입실을 먼저 해주세요"를 띄운다.
     */
    public String getQuizMode(String studentId, Integer contentId, String qlevel) {
        if ("02".equals(qlevel)) {
            return "COMPLETION";
        }
        boolean firstAttempt = clinicRepository.countPriorAttempts(studentId, contentId, "01") == 0;
        if (firstAttempt) return "FIRST";
        // 첫 제출 뒤엔 status가 항상 DONE이므로 grade로 가른다(2026-08-28) — 합격(KING/FRIEND)이면
        // 완료 화면, 불합격(grade null)이면 재도전 화면. 재도전으로 합격하면 grade가 채워져 완료 화면으로 바뀐다.
        ClinicRespDTO.RecommendLogStatusDTO logStatus = clinicRepository.findRecommendLogStatus(studentId, contentId);
        return (logStatus != null && logStatus.getGrade() != null) ? "COMPLETION" : "RETRY";
    }

    /**
     * 재도전(불합격) 중 제출 없이 "나가기"로 결과 화면에 갔을 때 보여줄 직전 제출 결과 — 새로 채점하지
     * 않고 recommend_log에 남은 마지막 기록을 그대로 조회한다(2026-08-25).
     */
    public ClinicRespDTO.QuizSubmitRespDTO getLastResult(String studentId, Integer contentId, String qlevel) {
        String resolvedQlevel = "02".equals(qlevel) ? "02" : "01";
        ClinicRespDTO.RecommendLogStatusDTO logStatus = clinicRepository.findRecommendLogStatus(studentId, contentId);
        if (logStatus == null || logStatus.getCorrectCount() == null || logStatus.getTotalCount() == null) {
            throw new Exception404("직전 제출 기록을 찾을 수 없습니다: studentId=" + studentId + ", contentId=" + contentId);
        }
        int totalCount = logStatus.getTotalCount();
        List<String> wrongQnums = clinicRepository.findLatestAnswersByRecommend(logStatus.getRecommendId(), resolvedQlevel).stream()
                .filter(a -> !Boolean.TRUE.equals(a.getCorrect()))
                .map(ClinicRespDTO.LatestAnswerDTO::getQnum)
                .toList();

        ClinicRespDTO.QuizSubmitRespDTO resp = new ClinicRespDTO.QuizSubmitRespDTO();
        resp.setAttemptNo(clinicRepository.countPriorAttemptRounds(logStatus.getRecommendId(), resolvedQlevel));
        // 처음/최종 점수를 함께 내려준다(2026-08-28). 화면 표시용 correctCount는 최종 점수 우선.
        Integer first = logStatus.getCorrectCount();
        Integer last = logStatus.getFinalCorrectCount() != null ? logStatus.getFinalCorrectCount() : first;
        resp.setCorrectCount(last != null ? last : 0);
        resp.setFirstCorrectCount(first);
        resp.setFinalCorrectCount(last);
        resp.setTotalCount(totalCount);
        resp.setPassLine((int) Math.ceil(totalCount * QUIZ_PASS_RATIO));
        // 첫 제출 뒤 status는 항상 DONE이므로 "합격 여부"는 grade로 판정한다(불합격이면 grade null → 재도전 화면).
        boolean passed = logStatus.getGrade() != null;
        resp.setPassed(passed);
        resp.setGrade(logStatus.getGrade());
        resp.setWrongQnums(wrongQnums);
        resp.setAlreadyCompleted(passed);  // grade 있음 = 합격한 적 있는 책
        resp.setNewBadges(List.of());
        // 새로 받은 뱃지는 없지만(직전 결과 재조회) 그 책에서 보유한 뱃지는 보상 칸에 보여준다
        resp.setBookBadge(clinicRepository.findBookBadge(studentId, contentId, "02".equals(resolvedQlevel)));
        // 재도전 화면에서도 레벨 카드 placeholder("Lv. 2", "35 / 96")가 노출되지 않도록 항상 채운다.
        String schoolyear = resolveSchoolyear(studentId);
        applyLevelStatus(resp, schoolyear, clinicRepository.countDoneBooksByGrade(studentId, schoolyear));
        applyStepStatus(resp, studentId, schoolyear);
        return resp;
    }

    /**
     * 완독(KING/FRIEND/심화완료) 후 홈으로 왔을 때 보여줄 "완료 화면" 상태 — 그 책에 남은 액션
     * (틀린 문제 다시 풀기/심화 문제 풀기)을 매번 최신 DB 상태로 다시 계산한다(2026-08-25).
     */
    public ClinicRespDTO.CompletionStateDTO getCompletionState(String studentId, Integer contentId) {
        ClinicRespDTO.RecommendBookDTO book = getBookInfo(contentId);
        ClinicRespDTO.RecommendLogStatusDTO logStatus = clinicRepository.findRecommendLogStatus(studentId, contentId);
        List<String> wrongQnums = List.of();
        if (logStatus != null) {
            wrongQnums = clinicRepository.findLatestAnswersByRecommend(logStatus.getRecommendId(), "01").stream()
                    .filter(a -> !Boolean.TRUE.equals(a.getCorrect()))
                    .map(ClinicRespDTO.LatestAnswerDTO::getQnum)
                    .toList();
        }
        // 심화 문항이 등록돼 있고(2026-08-31 추가) 아직 한 번도 안 푼 경우에만 "심화 문제 풀기" 노출.
        boolean hasAdvancedQuestions =
                !questionRepository.searchQuestions(contentId, "02", null, "S").isEmpty();
        int advancedAttempts = clinicRepository.countPriorAttempts(studentId, contentId, "02");
        boolean advancedAvailable = hasAdvancedQuestions && advancedAttempts == 0;

        // 심화 재도전/틀린문제 버튼(2026-08-31) — 심화를 한 번이라도 풀었고 아직 심화왕(만점)이 아니면 노출.
        Integer advBadgeId = clinicRepository.findAdvancedBadgeId(studentId, contentId);
        boolean advancedKing = advBadgeId != null && advBadgeId == BADGE_ADV_KING;
        boolean advancedRetryAvailable = hasAdvancedQuestions && advancedAttempts > 0 && !advancedKing;
        List<String> advancedWrongQnums = List.of();
        if (advancedRetryAvailable && logStatus != null) {
            advancedWrongQnums = clinicRepository.findLatestAnswersByRecommend(logStatus.getRecommendId(), "02").stream()
                    .filter(a -> !Boolean.TRUE.equals(a.getCorrect()))
                    .map(ClinicRespDTO.LatestAnswerDTO::getQnum)
                    .toList();
        }

        ClinicRespDTO.CompletionStateDTO resp = new ClinicRespDTO.CompletionStateDTO();
        resp.setBook(book);
        resp.setWrongQnums(wrongQnums);
        resp.setAdvancedAvailable(advancedAvailable);
        resp.setAdvancedRetryAvailable(advancedRetryAvailable);
        resp.setAdvancedWrongQnums(advancedWrongQnums);
        // 버튼 분기용(2026-08-28): KING=심화만 / FRIEND=재도전·틀린문제·심화 / null(불합격)=재도전만.
        resp.setGrade(logStatus != null ? logStatus.getGrade() : null);

        // "책 추천받기" 노출 여부 — recommendBook과 같은 규칙(회차당 2권 × 오늘 출석 회차 수)으로,
        // 오늘 한도를 다 썼으면 버튼을 아예 숨긴다(2026-08-28). 더해서 심화 게이트(2026-08-31)에
        // 걸려 있으면(직전 완독 책 심화 미응시) 심화를 풀 때까지 버튼을 숨긴다.
        int attendedSlots = Math.max(reservationService.countAttendedSlotsToday(studentId), 1);
        int maxAllowed = MAX_RECOMMENDATIONS_PER_SLOT * attendedSlots;
        int todayCount = clinicRepository.countTodayRecommends(studentId, KstClock.today());
        resp.setCanRecommendNext(todayCount < maxAllowed && !advancedGateBlocks(studentId));
        return resp;
    }

    /** 특정 책(contentId)의 정보만 조회 — 추천/대여 확정 등 부작용 없는 순수 조회 */
    public ClinicRespDTO.RecommendBookDTO getBookInfo(Integer contentId) {
        ClinicRespDTO.RecommendBookDTO card = clinicRepository.findBookCard(contentId, null);
        if (card == null) throw new Exception404("책 정보를 찾을 수 없습니다: contentId=" + contentId);
        return card;
    }

    /**
     * 심화 게이트 판정 (2026-08-31) — 개인 폰 앱에서 "책 추천받기"로 다음 책을 받으려면 직전에
     * 완독(DONE)한 책의 심화(qlevel=02) 문제를 최소 1회 제출했어야 한다. 아래 중 하나면 게이트를
     * 통과시킨다(= false 반환):
     *   - 완독 이력이 아예 없음 (생애 첫 책 등)
     *   - 직전 완독일이 오늘이 아님 — 날이 바뀌면 심화 미응시여도 다음 책 허용(사용자 확정 2026-08-31)
     *   - 그 책에 심화 문항이 아예 없음 (qlevel=02 미등록)
     *   - 그 책 심화 제출 1회 이상
     * 출석체크 기기(/attendance/enter)는 이 판정을 호출하지 않는다 — 항상 다음 책.
     */
    public boolean advancedGateBlocks(String studentId) {
        ClinicRespDTO.AdvancedGateDTO lastDone = clinicRepository.findLastDoneForGate(studentId);
        if (lastDone == null || lastDone.getCompletedDate() == null) return false;
        if (!KstClock.today().equals(lastDone.getCompletedDate())) return false;
        Integer contentId = lastDone.getContentId();
        boolean hasAdvancedQuestions =
                !questionRepository.searchQuestions(contentId, "02", null, "S").isEmpty();
        if (!hasAdvancedQuestions) return false;
        return clinicRepository.countPriorAttempts(studentId, contentId, "02") == 0;
    }

    /**
     * 책 확인 — 멱등 처리. 이미 추천받은 책이 있으면 그 책 그대로 반환(재추천/재대여 없음),
     * 없으면 규칙 1~5를 순서대로 적용해 새로 고르고 즉시 대여 + 추천 이력 기록까지 처리한다.
     * student-main 홈 화면에서는 더 이상 자동으로 호출하지 않고, "책 추천받기" 버튼 클릭 시에만 호출된다.
     *
     * 출석체크 기기(/attendance/enter)와 생애 첫 로그인은 심화 게이트를 적용하지 않는다 —
     * 이 오버로드(enforceAdvancedGate=false)로 호출한다.
     */
    @Transactional
    public ClinicRespDTO.RecommendBookDTO recommendBook(String studentId) {
        return recommendBook(studentId, false);
    }

    /**
     * @param enforceAdvancedGate true면 심화 게이트를 적용한다 — 개인 폰 앱 "책 추천받기" 버튼
     *        (POST /clinic/recommend)에서만 true. 직전 완독 책의 심화(qlevel=02)를 오늘 안에
     *        1회 이상 풀지 않았으면 다음 책을 내주지 않는다(2026-08-31, advancedGateBlocks 참고).
     */
    @Transactional
    public ClinicRespDTO.RecommendBookDTO recommendBook(String studentId, boolean enforceAdvancedGate) {
        ClinicRespDTO.RecommendBookDTO existing = clinicRepository.findPendingRecommendBookCard(studentId);
        if (existing != null) {
            // 실시간 모니터링 기준 "입실" 시점 — 이미 추천받은 책이 있는 재로그인도 여기서 입실 처리한다
            // (오늘 이미 열린 세션이 있으면 그대로 재사용하는 멱등 로직)
            monitorService.enterSession(studentId);
            // 퇴실로 대여만 반납되고 추천은 PENDING으로 남아있을 수 있다 — 재고가 있으면 다시 대여한다
            ensureActiveLoan(studentId, existing.getItemId());
            return existing;
        }

        // 심화 게이트(2026-08-31) — 개인 폰 앱 "책 추천받기"에서만. 직전 완독 책의 심화를 오늘 안 풀었으면 막는다.
        if (enforceAdvancedGate && advancedGateBlocks(studentId)) {
            throw new Exception400("심화 문제를 먼저 풀어야 다음 책을 받을 수 있어요.");
        }

        // 추천 상한 = 회차당 2권 × 오늘 출석(ATTENDED) 회차 수. 정상 흐름에선 이 지점에 오기 전
        // enterSession→markAttended가 이미 실행돼 출석 회차가 최소 1개는 있다 — 방어적으로 하한 1을 둔다.
        int attendedSlots = Math.max(reservationService.countAttendedSlotsToday(studentId), 1);
        int maxAllowed = MAX_RECOMMENDATIONS_PER_SLOT * attendedSlots;
        int todayCount = clinicRepository.countTodayRecommends(studentId, KstClock.today());
        if (todayCount >= maxAllowed) {
            throw new Exception400("오늘 추천받을 수 있는 책 " + maxAllowed + "권을 모두 받으셨습니다.");
        }
        log.info("학생 {}에게 새 추천 도서를 고릅니다", studentId);

        // 새로 추천한다는 건 이전 추천이 이미 DONE 처리됐다는 뜻(PENDING이면 위에서 그대로 반환됨).
        // 완독 시점에는 반납하지 않으므로(재도전 대비, submitQuiz 주석 참고) 다 읽은 그 책을 여기서
        // 반납한다 — 학생이 다음 책을 받았다는 건 이제 이전 책을 놓아줘도 된다는 뜻이다.
        // 반납이 일어나는 시점은 이 세 곳뿐이다:
        //   1) 학생 QR 퇴실 (StudentViewController.exitByQr → MonitorService.exitSession)
        //   2) 직원 퇴실 처리 (MonitorController.exit → MonitorService.exitSession)
        //   3) 새 책 추천 (여기)
        returnActiveLoanSafely(studentId);

        String centerCode = clinicRepository.findCenterCode(studentId);
        if (centerCode == null) throw new Exception404("학생의 소속 센터를 찾을 수 없습니다: " + studentId);
        log.info("학생 {}의 소속 센터: {}", studentId, centerCode);

        String schoolyear = resolveSchoolyear(studentId);
        log.info("학생 {}의 학년 기준 코드: {}", studentId, schoolyear);
        String year = String.valueOf(Year.now().getValue());
        log.info("현재 연도: {}", year);

        // 후보를 고르고(pickWithFallback) → 원자적으로 재고를 확보한다(reserveItemById). 고른 시점과
        // 확보 시점 사이에 다른 학생이 그 item의 마지막 한 권을 채가면 확보가 실패하는데(null), 그때는
        // 에러를 내지 않고 그 item을 제외 목록에 넣어 다음 후보로 넘어간다(2026-08-28) — 후보가 완전히
        // 소진되면 pickWithFallback이 null을 돌려주고, 그때 비로소 "추천할 도서 없음"으로 끝낸다.
        java.util.Set<Integer> triedItemIds = new java.util.HashSet<>();
        ClinicRespDTO.PickedItemDTO picked;
        Integer itemId;
        while (true) {
            picked = pickWithFallback(studentId, centerCode, year, schoolyear, triedItemIds);
            if (picked == null) throw new Exception404("추천할 수 있는 도서가 더 이상 없습니다.");
            log.info("학생 {}에게 추천할 도서 content_id={}, item_id={}", studentId, picked.getContentId(), picked.getItemId());

            itemId = bookRepository.reserveItemById(picked.getItemId());
            if (itemId != null) break;
            log.info("학생 {} — item_id={} 재고 확보 실패(그 사이 소진), 다음 후보로", studentId, picked.getItemId());
            triedItemIds.add(picked.getItemId());
        }
        log.info("학생 {}에게 대여할 도서 item_id: {}", studentId, itemId);

        bookRepository.insertItemLoan(itemId, studentId);
        clinicRepository.insertRecommendLog(studentId, picked.getContentId(), itemId);
        // 실시간 모니터링 기준 "입실" 시점 — 미입실 예약 카드가 여기서 입실로 전환된다
        monitorService.enterSession(studentId);
        // 이 책의 recommended_at(경과 시간 계산 기준점)이 새로 생겼다 — 이전 책을 풀다가 제출 없이
        // 나가서 남아있던 quiz_started_at(그보다 이전 시각)이 그대로면, 경과시간(DATEDIFF)이
        // "새 recommended_at − 옛 quiz_started_at"이 되어 음수로 나온다(2026-08-26 발견). 새 추천
        // 시점에 확실히 지워서 이 기준점 역전을 막는다.
        monitorService.exitQuiz(studentId);
        ClinicRespDTO.RecommendBookDTO card = clinicRepository.findBookCard(picked.getContentId(), itemId);
        log.info("학생 {}에게 추천된 도서 정보: {}", studentId, card);
        return card;
    }

    /**
     * 규칙 3(dedup) → 규칙 4(dedup 해제) → 규칙 5(윗학년 순차) 순서로 후보를 찾는다 — item(실물 판본) 단위 선택.
     * excludeItemIds: 고른 뒤 원자적 재고 확보에 실패한(그 사이 마지막 한 권이 나간) item들 — 이번 추천에서 건너뛴다.
     */
    private ClinicRespDTO.PickedItemDTO pickWithFallback(String studentId, String centerCode, String year, String schoolyear,
                                                         java.util.Collection<Integer> excludeItemIds) {
        log.info("학생 {}에게 추천할 도서를 찾습니다: centerCode={}, year={}, schoolyear={}, 제외 item={}",
                studentId, centerCode, year, schoolyear, excludeItemIds);
        ClinicRespDTO.LastRecommendDTO last = clinicRepository.findLastRecommend(studentId);
        log.info("학생 {}의 직전 추천 도서: {}", studentId, last);
        String lastType = last == null ? null : last.getContentType();
        log.info("학생 {}의 직전 추천 도서 content_type: {}", studentId, lastType);
        String lastGenre = last == null ? null : last.getGenre();
        log.info("학생 {}의 직전 추천 도서 genre: {}", studentId, lastGenre);

        ClinicRespDTO.PickedItemDTO picked = clinicRepository.pickNextItem(
                studentId, centerCode, year, schoolyear, lastType, lastGenre, true, excludeItemIds);
        if (picked != null) return picked;
        log.info("학생 {}에게 추천할 도서 후보가 없습니다 — dedup 조건 해제 후 다시 시도합니다", studentId);

        // 규칙 4: 처음으로 돌아가 dedup 조건 없이 다시 추천
        picked = clinicRepository.pickNextItem(
                studentId, centerCode, year, schoolyear, null, null, false, excludeItemIds);
        if (picked != null) return picked;
        log.info("학생 {}에게 추천할 도서 후보가 여전히 없습니다 — 한 단계 위 학년부터 순차적으로 시도합니다", studentId);

        // 규칙 5: 현재 학년 책을 모두 추천받았다면 한 단계 위 학년부터 순차적으로 시도
        int current = parseSchoolyear(schoolyear);
        for (int sy = current + 1; sy <= MAX_SCHOOLYEAR; sy++) {
            String candidate = String.format("%02d", sy);
            picked = clinicRepository.pickNextItem(
                    studentId, centerCode, year, candidate, null, null, false, excludeItemIds);
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
                                                        String mode, List<ClinicReqDTO.AnswerDTO> answers) {
        String resolvedQlevel = "02".equals(qlevel) ? "02" : "01";
        boolean advanced = "02".equals(resolvedQlevel);
        boolean wrongOnly = "WRONG_ONLY".equalsIgnoreCase(mode);  // "틀린 문제 다시 풀기" — 점수/등급/뱃지 불변

        List<QuestionRespDTO.QuestionDTO> questions = questionRepository.searchQuestions(contentId, resolvedQlevel, null, "S");
        if (questions.isEmpty()) throw new Exception404("문제를 찾을 수 없습니다: contentId=" + contentId);

        ClinicRespDTO.RecommendLogStatusDTO logStatus = clinicRepository.findRecommendLogStatus(studentId, contentId);
        if (logStatus == null) throw new Exception404("추천 이력을 찾을 수 없습니다: studentId=" + studentId + ", contentId=" + contentId);

        // 뱃지는 "첫 시도" 결과로 등급이 정해진다(재도전 고정) — 이번 제출의 로그를 적재하기 전에
        // 같은 도전(recommend_id)+난이도의 기존 제출 회차 수로 첫 시도 여부/이번이 몇 번째 시도인지를 판단한다.
        int priorAttemptRounds = clinicRepository.countPriorAttemptRounds(logStatus.getRecommendId(), resolvedQlevel);
        boolean firstAttempt = priorAttemptRounds == 0;

        Map<String, Integer> submittedByQnum = answers.stream()
                .collect(Collectors.toMap(ClinicReqDTO.AnswerDTO::getQnum, ClinicReqDTO.AnswerDTO::getSelected, (a, b) -> b));

        // 첫 시도가 아니면(이미 한 번 이상 제출한 적 있음) "틀린 문제만 다시 풀기"처럼 일부 문항만
        // 다시 제출됐을 수 있다. 이번에 다시 제출되지 않은 문항은 미제출(오답) 처리하지 않고, 직전
        // 제출 기록의 정답 여부를 그대로 이어받아 합산한다 — 안 그러면 12문제 중 2문제만 다시 풀어도
        // 나머지 10문제가 전부 "미제출=오답"으로 깎여버린다(2026-08-25 발견).
        Map<String, Boolean> priorCorrectByQnum = firstAttempt
                ? Map.of()
                : clinicRepository.findLatestAnswersByRecommend(logStatus.getRecommendId(), resolvedQlevel).stream()
                        .collect(Collectors.toMap(ClinicRespDTO.LatestAnswerDTO::getQnum, ClinicRespDTO.LatestAnswerDTO::getCorrect));

        int totalCount = questions.size();
        int correctCount = 0;
        List<ClinicReqDTO.AnswerLogDTO> answerLogs = new ArrayList<>();
        List<String> wrongQnums = new ArrayList<>();
        for (QuestionRespDTO.QuestionDTO q : questions) {
            Integer selected = submittedByQnum.get(q.getQnum());
            if (selected != null) {
                // 이번에 실제로 제출된 문항 — 새로 채점하고 풀이 이력에 남긴다
                boolean correct = String.valueOf(selected).equals(q.getAns());
                if (correct) correctCount++; else wrongQnums.add(q.getQnum());
                ClinicReqDTO.AnswerLogDTO logRow = new ClinicReqDTO.AnswerLogDTO();
                logRow.setQnum(q.getQnum());
                logRow.setSelected(selected);
                logRow.setCorrect(correct);
                logRow.setQtype(q.getQtype());
                answerLogs.add(logRow);
            } else if (firstAttempt) {
                // 첫 시도인데 미제출(또는 존재하지 않는 qnum만 담아 보낸 위조 제출) 문항은 오답으로
                // 기록한다. 문항마다 항상 로그를 남겨야 countPriorAttempts가 "이번 제출이 있었다"를
                // 놓치지 않는다 — 안 그러면 문제 번호를 실제 문항과 다르게 보내는 "빈 제출"을 반복해
                // 뱃지를 중복 획득할 수 있다.
                wrongQnums.add(q.getQnum());
                ClinicReqDTO.AnswerLogDTO logRow = new ClinicReqDTO.AnswerLogDTO();
                logRow.setQnum(q.getQnum());
                logRow.setSelected(0); // 0 = 미제출(보기 1~4 범위 밖 sentinel)
                logRow.setCorrect(false);
                logRow.setQtype(q.getQtype());
                answerLogs.add(logRow);
            } else {
                // 재제출인데 이번엔 다시 내지 않은 문항 — 직전 제출 기록의 정답 여부를 그대로
                // 이어받는다(기록이 없으면 오답 취급). 이번에 실제로 풀지 않았으므로 이력에 새로
                // 남기지는 않는다.
                boolean correct = Boolean.TRUE.equals(priorCorrectByQnum.get(q.getQnum()));
                if (correct) correctCount++; else wrongQnums.add(q.getQnum());
            }
        }

        ClinicRespDTO.QuizSubmitRespDTO resp = new ClinicRespDTO.QuizSubmitRespDTO();
        resp.setAttemptNo(priorAttemptRounds + 1);
        int passLine = (int) Math.ceil(totalCount * QUIZ_PASS_RATIO);
        resp.setCorrectCount(correctCount);
        resp.setTotalCount(totalCount);
        resp.setPassLine(passLine);
        // 오답 문항은 서버가 알려준다 — 화면이 정답을 들고 있지 않아도 "틀린 문제 풀기"가 동작해야
        // /question/search에서 ans를 빼도 기능이 깨지지 않는다(2026-08-20).
        resp.setWrongQnums(wrongQnums);

        // 풀이 이력 적재 — 이번에 실제로 제출된 문항만 남긴다(재제출에서 다시 내지 않은 문항까지
        // 미제출로 기록하면 풀이 이력이 오염된다, 2026-08-25)
        if (!answerLogs.isEmpty()) {
            clinicRepository.insertQuizAnswerLogs(logStatus.getRecommendId(), studentId, contentId, resolvedQlevel, answerLogs);
        }

        // 심화문제는 완독/등급/레벨 개념이 없다 — 이력 기록/채점 결과에 더해 뱃지(심화완료/심화왕)만 판정.
        // 기본 문제와 같은 방식으로(2026-08-31) 만점이 아니면 재도전(RETRY)과 틀린 문제 다시 풀기(WRONG_ONLY)를
        // 열어준다. "처음 점수"(diary.advanced_correct_cnt)는 고정, "최종 점수"(advanced_final_correct_cnt)는
        // 재도전에서 더 잘하면 올라간다. 뱃지도 재도전으로 "올라가기만" 한다(불합격→4/5, 4→5). 내려가지 않음.
        if (advanced) {
            boolean advRetry = !firstAttempt && !wrongOnly;   // "심화 재도전" — 최종 점수 + (오를 때만) 뱃지 갱신
            resp.setPassed(correctCount >= passLine);
            resp.setGrade(null);

            if (firstAttempt) {
                resp.setNewBadges(awardAdvancedBadge(studentId, contentId, correctCount, totalCount, passLine, true));
                recordDiarySafely(studentId, contentId, logStatus.getRecommendId(), resolvedQlevel, correctCount, totalCount);
            } else if (advRetry) {
                resp.setNewBadges(upgradeAdvancedBadge(studentId, contentId, correctCount, totalCount, passLine));
                // 최종 점수만 max로 갱신한다(upsertDiaryDetail이 처음 점수는 COALESCE로 보존)
                recordDiarySafely(studentId, contentId, logStatus.getRecommendId(), resolvedQlevel, correctCount, totalCount);
            } else {
                // 틀린 문제만 다시 풀기 — 점수/뱃지 어떤 것도 바꾸지 않는다
                resp.setNewBadges(List.of());
            }

            // 심화는 레벨이 바뀌진 않지만, 레벨 카드에 현재 레벨은 그대로 보여줘야 한다
            // (안 채우면 결과 화면 HTML의 placeholder "Lv. 2"가 그대로 노출된다)
            String schoolyear = resolveSchoolyear(studentId);
            applyLevelStatus(resp, schoolyear, clinicRepository.countDoneBooksByGrade(studentId, schoolyear));
            applyStepStatus(resp, studentId, schoolyear);
            // 새로 받은 뱃지가 없어도(틀린문제 재제출 등) 그 책의 심화 뱃지를 보상 칸에 계속 보여준다
            resp.setBookBadge(clinicRepository.findBookBadge(studentId, contentId, true));
            syncMonitorSafely(studentId);
            return resp;
        }

        // ── 기본(qlevel=01) — 2026-08-28 결과 프로세스 재확정 ──
        // 첫 제출이면 합격/불합격 무관하게 status=DONE. 불합격이어도 다음 입실 시 다음 책을 받고,
        // 재도전은 결과화면/완료화면 버튼에서만 이어서 한다. 점수는 처음/최종으로 분리 보관한다.
        //   correct_count(처음 점수)  — 최초 제출값에서 고정
        //   final_correct_count(최종) — 재도전(mode=RETRY)에서 "더 잘한 경우에만" 갱신(max).
        //   grade + 뱃지               — 재도전으로 "올라가기만" 한다(null→FRIEND→KING). 독서친구→재도전 불합격
        //                                처럼 내려가는 방향은 반영 안 함. 등급이 오르면 기본 뱃지(1~3)도 상위로 교체 —
        //                                그래야 grade·뱃지·"독서왕 횟수"가 항상 일치한다(2026-08-28).
        //   "틀린 문제 다시 풀기"(WRONG_ONLY) — 점수/등급/뱃지 어떤 것도 바꾸지 않는다.
        boolean passed = correctCount >= passLine;
        String freshGrade = !passed ? null : (correctCount == totalCount ? "KING" : "FRIEND");
        boolean retry = !firstAttempt && !wrongOnly;   // "재도전" — 최종 점수 + (오를 때만) 등급/뱃지 갱신

        Integer frozenFirst = logStatus.getCorrectCount();
        Integer frozenFinal = logStatus.getFinalCorrectCount() != null ? logStatus.getFinalCorrectCount() : frozenFirst;
        String oldGrade = logStatus.getGrade();
        // 재도전은 등급을 올리기만 한다. 첫 제출은 이번 결과 그대로.
        String effectiveGrade = firstAttempt ? freshGrade
                : (retry ? higherGrade(oldGrade, freshGrade) : oldGrade);
        boolean gradeUpgraded = retry && gradeRank(effectiveGrade) > gradeRank(oldGrade);

        if (firstAttempt) {
            // 처음 점수 = 최종 점수 = 이번 값, grade = 이번 결과, status = DONE(불합격이어도)
            clinicRepository.updateRecommendResult(logStatus.getRecommendId(), correctCount, totalCount, effectiveGrade, "DONE");
            resp.setPassed(passed);
            resp.setGrade(effectiveGrade);
            resp.setFirstCorrectCount(correctCount);
            resp.setFinalCorrectCount(correctCount);
        } else if (retry) {
            // 재도전 — 최종 점수는 기존보다 높을 때만 올리고(못 풀었으면 그대로), 등급도 오를 때만 갱신.
            int prevFinal = frozenFinal != null ? frozenFinal : (frozenFirst != null ? frozenFirst : correctCount);
            int bestFinal = Math.max(correctCount, prevFinal);
            clinicRepository.updateRetryResult(logStatus.getRecommendId(), bestFinal, effectiveGrade);
            resp.setPassed(effectiveGrade != null);
            resp.setGrade(effectiveGrade);
            resp.setFirstCorrectCount(frozenFirst);
            resp.setFinalCorrectCount(bestFinal);
        } else {
            // 틀린 문제 다시 풀기 — 점수/등급 어떤 것도 바꾸지 않는다. 화면은 원래(고정) 등급으로 보여주되,
            // "이번에 몇 개 맞혔는지"(correctCount, 위에서 병합 계산)와 남은 오답(wrongQnums)만 갱신해
            // "틀린 문제" 진행 상황을 보여준다.
            resp.setPassed(oldGrade != null);
            resp.setGrade(oldGrade);
            resp.setFirstCorrectCount(frozenFirst);
            resp.setFinalCorrectCount(frozenFinal);
        }
        // "이미 완독한 책" 안내는 이번 제출 전에 이미 합격(grade 있음) 상태였을 때만.
        resp.setAlreadyCompleted(!firstAttempt && oldGrade != null);

        // 레벨/독서탐험 — 첫 제출이면 status=DONE이라 재도전/통과/만점 구분 없이 완독 1권으로 카운트된다.
        // 재도전 결과 화면에서도 placeholder("Lv. 2", "35 / 96")가 노출되지 않도록 항상 채운다(2026-08-25).
        String schoolyear = resolveSchoolyear(studentId);
        int doneNow = clinicRepository.countDoneBooksByGrade(studentId, schoolyear);
        applyLevelStatus(resp, schoolyear, doneNow);
        applyStepStatus(resp, studentId, schoolyear);

        if (firstAttempt) {
            // 완독(또는 첫 제출)해도 여기서 책을 반납하지 않는다(2026-07-31, 재도전 대비) — 반납은
            // 퇴실(MonitorService.exitSession) / 다음 책 추천(recommendBook) 때만 일어난다.
            LevelRule rule = LEVEL_RULES.get(schoolyear);
            if (rule != null) {
                resp.setLeveledUp(resp.getLevelNo() > levelFor(doneNow - 1, rule.booksPerLevel()));
            }
        } else {
            resp.setLeveledUp(false);
        }

        // 온라인 카드 — 이 책 첫 제출에서만 지급(책당 1장, 합격/불합격 무관). 재도전/틀린문제 재제출은 지급 없음.
        // NORMAL 카드가 CARD_SET_SIZE(10)의 배수를 채운 순간 온라인 레어카드도 함께 지급하고,
        // 그 시점을 cardRewardReached로 알려 오프라인 실물 1장 교환 안내를 띄운다.
        if (firstAttempt && !clinicRepository.existsNormalCard(studentId, contentId)) {
            clinicRepository.insertNormalCard(studentId, contentId);
            ClinicRespDTO.CardDTO card = clinicRepository.findCardByContent(contentId);
            int totalCards = clinicRepository.countNormalCards(studentId); // 이번 지급 포함
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

        // 기본 문제 뱃지 — 첫 제출은 이번 결과로 지급. 재도전으로 등급이 올라간 경우엔 기존 기본 뱃지(1~3)를
        // 지우고 상위 뱃지로 교체한다(내려가는 방향은 gradeUpgraded=false라 손대지 않는다).
        if (firstAttempt) {
            resp.setNewBadges(awardBasicBadge(studentId, contentId, correctCount, totalCount, passLine, firstAttempt));
        } else if (gradeUpgraded) {
            clinicRepository.deleteBasicBadge(studentId, contentId);
            resp.setNewBadges(awardBookBadge(studentId, contentId, "KING".equals(effectiveGrade) ? BADGE_KING : BADGE_FRIEND));
        } else {
            resp.setNewBadges(List.of());
        }
        // 미달(참 잘했어요) / 독서친구 / 독서왕 — 세 등급 모두 뱃지가 있으므로 첫 제출 뒤에는 항상 값이 있다.
        // 재도전·틀린문제 재제출이라 newBadges가 비어도 결과화면 보상 칸이 비지 않게 한다(2026-09-01).
        resp.setBookBadge(clinicRepository.findBookBadge(studentId, contentId, false));

        // 독서일지 — "처음 점수"만 스냅샷으로 남긴다(그날 기록). 재도전/틀린문제 재제출은 basic_correct_cnt를
        // 바꾸지 않는다 — "최종 점수"는 recommend_log.final_correct_count에서 조회해 함께 보여준다.
        int diaryCorrect = firstAttempt ? correctCount : (frozenFirst != null ? frozenFirst : correctCount);
        recordDiarySafely(studentId, contentId, logStatus.getRecommendId(), resolvedQlevel, diaryCorrect, totalCount);
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

    /** 현재 대여 중인 도서를 반납 처리한다(없으면 조용히 넘어감) — 다음 책 추천 시점에 호출된다 */
    private void returnActiveLoanSafely(String studentId) {
        BookRespDTO.ItemLoanRespDTO activeLoan = bookRepository.findActiveLoanByStudent(studentId);
        if (activeLoan == null) return;
        bookRepository.updateLoanReturned(activeLoan.getLoanId());
        bookRepository.markItemReturned(activeLoan.getItemId());
        log.info("학생 {}의 이전 대여 도서를 반납 처리했습니다: loanId={}, itemId={}",
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

    /**
     * 심화(02) 재도전 뱃지 — "올라가기만" 한다(2026-08-31). 이번 제출이 합격선 이상이고, 그 결과 뱃지가
     * 지금 보유한 것보다 높으면(없음&lt;심화완료&lt;심화왕) 기존 심화 뱃지를 지우고 상위 뱃지로 교체한다.
     * 그대로거나 내려가는 방향이면 빈 목록.
     */
    private List<ClinicRespDTO.BadgeDTO> upgradeAdvancedBadge(String studentId, Integer contentId,
                                                              int correct, int total, int passLine) {
        if (correct < passLine) return List.of();
        int targetId = (correct >= total) ? BADGE_ADV_KING : BADGE_ADV_DONE;
        Integer currentId = clinicRepository.findAdvancedBadgeId(studentId, contentId);
        if (advancedBadgeRank(targetId) <= advancedBadgeRank(currentId)) return List.of();
        clinicRepository.deleteAdvancedBadge(studentId, contentId);
        return awardBookBadge(studentId, contentId, targetId);
    }

    /** 심화 뱃지 순위 — null(없음)=0 &lt; 심화완료(4)=1 &lt; 심화왕(5)=2 */
    private int advancedBadgeRank(Integer badgeId) {
        if (badgeId == null) return 0;
        if (badgeId == BADGE_ADV_KING) return 2;
        if (badgeId == BADGE_ADV_DONE) return 1;
        return 0;
    }

    /** (student, content, badge) 1건 적재 후 결과화면 팝업용 뱃지 정보를 반환 */
    private List<ClinicRespDTO.BadgeDTO> awardBookBadge(String studentId, Integer contentId, int badgeId) {
        clinicRepository.insertStudentBadge(studentId, contentId, badgeId);
        ClinicRespDTO.BadgeDTO badge = clinicRepository.findBadge(badgeId);
        log.info("학생 {} 뱃지 획득: 책 {} [{}] {}", studentId, contentId, badgeId,
                 badge == null ? "" : badge.getBadgeName());
        return badge == null ? List.of() : List.of(badge);
    }

    /** 두 등급 중 더 높은 쪽 (null &lt; FRIEND &lt; KING) — 재도전은 등급을 올리기만 한다(2026-08-28) */
    private String higherGrade(String a, String b) {
        return gradeRank(a) >= gradeRank(b) ? a : b;
    }

    private int gradeRank(String grade) {
        if ("KING".equals(grade)) return 2;
        if ("FRIEND".equals(grade)) return 1;
        return 0;   // null(불합격)
    }

    /** 완독 권수 → 레벨 (필요권수마다 1레벨, 1레벨부터 시작, 만렙 MAX_LEVEL로 상한) */
    private int levelFor(int doneBooks, int booksPerLevel) {
        if (doneBooks <= 0 || booksPerLevel <= 0) return 1;
        return Math.min(MAX_LEVEL, doneBooks / booksPerLevel + 1);
    }

    /**
     * 결과 화면 레벨 카드(levelNo/levelTitle/progressPercent/booksToNextLevel)를 채운다 — 새로 완독한
     * 직후뿐 아니라 "이미 완독한 책 재제출"/"새로고침 후 직전 결과 조회"에서도 호출해야 한다. 이 값들을
     * 안 채우면 QuizSubmitRespDTO의 필드가 null로 남아 결과 화면 HTML에 박혀있는 placeholder("Lv. 2")가
     * 그대로 노출되는 문제가 있었다(2026-08-25 발견 — 실제 레벨과 다른 값이 화면에 보임).
     */
    /**
     * 독서탐험 진행 칸(stepNow/stepTotal)을 채운다 — applyLevelStatus와 마찬가지로 "새로 완독한 시점"
     * 뿐 아니라 재제출/심화/새로고침 등 결과 화면이 열리는 모든 경로에서 호출해야 한다. 안 그러면
     * QuizSubmitRespDTO 필드가 null로 남아 결과 화면 HTML의 placeholder("35 / 96")가 노출된다
     * (2026-08-25 — applyLevelStatus만 모든 경로에 붙이고 이건 빠뜨려서 재발).
     */
    private void applyStepStatus(ClinicRespDTO.QuizSubmitRespDTO resp, String studentId, String schoolyear) {
        Integer stepTotal = STEP_TOTAL_BY_SCHOOLYEAR.get(schoolyear);
        if (stepTotal == null) return;
        resp.setStepNow(clinicRepository.countDoneBooksThisYear(studentId));
        resp.setStepTotal(stepTotal);
    }

    private void applyLevelStatus(ClinicRespDTO.QuizSubmitRespDTO resp, String schoolyear, int doneBooks) {
        LevelRule rule = LEVEL_RULES.get(schoolyear);
        if (rule == null) return;
        int booksPerLevel = rule.booksPerLevel();
        int levelNo = levelFor(doneBooks, booksPerLevel);
        resp.setLevelNo(levelNo);
        resp.setLevelTitle(clinicRepository.findLevelTitle(schoolyear, levelNo));
        if (levelNo >= MAX_LEVEL) {
            resp.setProgressPercent(100);
            resp.setBooksToNextLevel(0);
        } else {
            int inLevel = doneBooks % booksPerLevel;
            resp.setProgressPercent((int) Math.round(inLevel * 100.0 / booksPerLevel));
            resp.setBooksToNextLevel(booksPerLevel - inLevel);
        }
    }

    private int parseSchoolyear(String schoolyear) {
        try {
            return Integer.parseInt(schoolyear);
        } catch (NumberFormatException e) {
            return Integer.parseInt(FALLBACK_SCHOOLYEAR);
        }
    }

    /** 학생의 실제 학년(grade_key)을 학년 기준 로직에 쓸 S코드로 변환 — 미등록 학생은 기본값으로 대체 */
    /**
     * 클리닉 추천 기준 학년 — clinic_grade_key(book_clinic 자체 관리)가 있으면 그대로 쓰고,
     * 없으면 grade_key(올패스 코드)를 변환해서 최초 1회 채워넣는다(lazy init). 한 번 채워지면
     * 이후 올패스 쪽에서 grade_key(진급 등)가 바뀌어도 이 값은 따라가지 않는다 — "초1인데 초2
     * 수준 책을 추천받는" 것처럼 실제 학년과 클리닉 추천 기준을 분리하기 위해서다(2026-08-07).
     */
    private String resolveSchoolyear(String studentId) {
        String clinicGradeKey = clinicRepository.findClinicGradeKey(studentId);
        if (clinicGradeKey != null && !clinicGradeKey.isBlank()) {
            return clinicGradeKey;
        }

        String gradeKey = clinicRepository.findGradeKey(studentId);
        String schoolyear;
        if (gradeKey == null || gradeKey.isBlank()) {
            schoolyear = FALLBACK_SCHOOLYEAR;
        } else {
            String mapped = OLPASS_GRADE_TO_SCHOOLYEAR.get(gradeKey);
            if (mapped != null) {
                schoolyear = mapped;
            } else {
                // 매핑에 없는 값 — 이런 코드가 나타나면 올패스 코드표가 바뀌었다는 뜻이니 위
                // 맵을 다시 확인해야 한다. book_clinic 자체 코드(01~07)일 가능성도 있어 그대로 쓴다.
                log.warn("학생 {}의 grade_key({})가 올패스 매핑에 없습니다 — 원본 값을 그대로 사용합니다", studentId, gradeKey);
                schoolyear = gradeKey;
            }
        }

        clinicRepository.updateClinicGradeKey(studentId, schoolyear);
        return schoolyear;
    }
}
