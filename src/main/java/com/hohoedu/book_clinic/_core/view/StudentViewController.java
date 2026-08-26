package com.hohoedu.book_clinic._core.view;

import java.util.Map;

import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.handler.exception.Exception404;
import com.hohoedu.book_clinic._core.interceptor.StudentSessionRegistry;
import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic.clinic.ClinicService;
import com.hohoedu.book_clinic.clinic._dto.ClinicRespDTO;
import com.hohoedu.book_clinic.kiosk.KioskService;
import com.hohoedu.book_clinic.monitor.MonitorService;
import com.hohoedu.book_clinic.pass.PassService;
import com.hohoedu.book_clinic.reservation.ReservationService;
import com.hohoedu.book_clinic.student.StudentRepository;
import com.hohoedu.book_clinic.student.model.Student;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * 학생(PWA) 화면 라우팅 — 문제풀이 앱(/student/**), 출석체크 앱(/attendance/**), 두 화면을 고르는
 * 런처(/launch)까지 전부 같은 manifest.json 하나로 설치되는 앱이라 한 컨트롤러에 모아둔다
 * (2026-07-30 재설계로 앱을 2개에서 1개로 합침 — 별도 PWA로 나누면 설치 프롬프트가 브라우저별로
 * 불안정했다). 학부모 앱(Flutter, /app/install) 설치 랜딩도 같은 성격(학생·학부모용 진입 화면)이라
 * 여기 함께 둔다.
 *
 * 흐름은 로그인 모드로 분기하지 않는다: 로그인 → 책 추천(없으면 새로 추천, 있으면 그 책 그대로) →
 * 로그아웃 → (오프라인 독서) → 재로그인 → 문제풀기 → 결과 확인. 메인 화면은 항상 같은 화면이고,
 * "문제 풀기" 버튼 하나만 있다 — 언제 누를지는 학생 자유.
 *
 * 인증(2026-07-31): QR 로그인이 실제 신원 확인을 하는 곳은 /student/login 뿐이었고, 그 뒤 화면들은
 * URL의 studentId 파라미터가 DB에 존재하기만 하면 그대로 통과시켰다 — studentId는 QR의 appId처럼
 * 비밀값이 아니라 평범한 학번류라, 다른 학생의 studentId만 알면 로그인 없이 그 학생 화면에 들어갈 수
 * 있었다. 로그인 성공 시 HttpSession에 studentId를 심어두고, 이후 화면은 그 세션값만 신뢰한다 —
 * URL의 studentId 파라미터는 화면 전환용일 뿐 신원 증명으로 쓰지 않는다(세션 값과 다르면 재로그인).
 */
@Controller
@RequiredArgsConstructor
public class StudentViewController {

    private static final String SESSION_STUDENT_ID = "studentId";

    private final StudentRepository studentRepository;
    private final ClinicService clinicService;
    private final MonitorService monitorService;
    private final KioskService kioskService;
    private final PassService passService;
    private final ReservationService reservationService;
    private final Environment environment;
    private final StudentSessionRegistry studentSessionRegistry;

    // ── 런처 ─────────────────────────────────────────────────────────────

    /**
     * 앱 하나(manifest.json)로 설치된 PWA의 진짜 진입점(start_url)이다.
     * 처음 켰을 때만 "문제풀이"/"출석체크"를 고르게 하고, 그 선택은 브라우저 localStorage에 저장해
     * 이후로는 이 화면 없이 바로 골랐던 화면으로 넘어간다(launcher.js가 처리) — 실제 라우팅 분기는
     * 서버가 아니라 클라이언트에서 한다(로그인 여부와 무관하게 그냥 "이 기기 용도"만 기억하면 되므로).
     *
     * dev 프로파일에서는 테스트 중 두 화면을 계속 오가며 확인해야 해서, 저장된 값이 있어도 매번
     * 선택 화면을 다시 보여준다 — prod에서는 원래대로 한 번만 물어본다.
     */
    @GetMapping("/launch")
    public String getLauncherPage(Model model) {
        boolean isDev = environment.matchesProfiles("dev");
        model.addAttribute("forceChoice", isDev);
        return "/student/launcher";
    }

    // ── 문제풀이 앱 ──────────────────────────────────────────────────────

    /**
     * 세션에 로그인된 studentId가 없으면(로그인 안 함/세션 만료) null. 세션은 있는데 URL의
     * studentId가 다른 학생을 가리키면(다른 학생 화면을 직접 열어보려는 시도) 역시 null —
     * 세션값이 항상 우선이고, URL 파라미터는 그것과 일치할 때만(또는 비어있을 때만) 유효하다.
     */
    private String requireSessionStudentId(HttpServletRequest request, String requestedStudentId) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object sessionStudentId = session.getAttribute(SESSION_STUDENT_ID);
        if (sessionStudentId == null) {
            return null;
        }
        if (requestedStudentId != null && !requestedStudentId.isBlank() && !sessionStudentId.equals(requestedStudentId)) {
            return null;
        }
        String studentId = (String) sessionStudentId;
        // 다른 기기에서 새로 로그인했거나(중복 로그인) 직원이 퇴실 처리했으면 이 세션은 더 이상
        // 유효하지 않다 — 강제 로그아웃시킨다(2026-08-26). 문제풀이 도중이라도 다음 요청(페이지 이동
        // 또는 student-question.js의 주기적 세션 확인)에서 바로 걸린다.
        if (!studentSessionRegistry.isActive(studentId, session.getId()) || monitorService.hasExitedToday(studentId)) {
            session.invalidate();
            return null;
        }
        return studentId;
    }

    /** 학생 로그인 화면 — 실제 QR 스캔 대신 appId를 직접 입력해서 테스트하는 임시 화면 */
    @GetMapping({ "/student", "/student/", "/student/login" })
    public String getLoginPage() {
        return "/student/student-login";
    }

    /**
     * 학생 QR 자가 퇴실 — 두 군데에서 호출된다.
     *   1) student-main의 "퇴실하기" 버튼 — studentId(로그인된 본인)를 같이 보낸다. 스캔된 appId가
     *      그 studentId 본인 것인지 반드시 확인한다 — 확인 없이 스캔된 appId만 믿으면, 다른 학생 QR을
     *      잘못 스캔했을 때 그 학생이 세션도 없는 채로 조용히 무시되고, 정작 로그인된 학생은 화면만
     *      로그아웃될 뿐 실제 퇴실은 안 찍히는 문제가 있었다(2026-07-30, 실사용 중 발견).
     *   2) 출석체크 PWA의 "퇴실" — 로그인 컨텍스트 자체가 없어 studentId를 안 보낸다. 이 경우엔
     *      대조할 "본인"이 없으므로 스캔된 QR을 그대로 신뢰한다.
     */
    @PostMapping("/student/exit")
    @ResponseBody
    public ResponseEntity<?> exitByQr(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String studentId = body.get("studentId");
        String appId = body.get("appId");
        Student student = studentRepository.findByAppId(appId);
        if (student == null) {
            throw new Exception404("일치하는 학생 정보를 찾을 수 없어요. QR을 다시 스캔해주세요.");
        }
        kioskService.assertSameCenter(request, student.getCenterCode());
        if (studentId != null && !studentId.isBlank() && !student.getStudentId().equals(studentId)) {
            throw new Exception400("본인 학생증 QR이 아니에요. 다시 확인해주세요.");
        }
        monitorService.exitSession(student.getStudentId());
        return ResponseEntity.ok(ApiUtils.success(null));
    }

    /**
     * 문제풀이 앱 로그아웃 — student-main.js/student-result.js의 "로그아웃" 버튼이 호출한다(2026-08-26).
     * 예전엔 클라이언트가 로컬 저장소만 지우고 서버 세션은 그대로 둬서(HttpSession 살아있음),
     * 모니터링에 "문제 푸는 중"이 계속 남는 등 서버가 로그아웃 사실을 전혀 몰랐다. 퇴실(QR)과는
     * 다른 개념이라(로그아웃해도 오늘 입실 상태는 유지되어야 재로그인 후 이어 읽을 수 있다)
     * monitorService.exitSession은 호출하지 않는다 — 세션 무효화 + "문제 푸는 중"/"결과 확인중"
     * 표시만 해제한다.
     */
    @PostMapping("/student/logout")
    @ResponseBody
    public ResponseEntity<?> logout(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String studentId = requireSessionStudentId(request, body.get("studentId"));
        if (studentId != null) {
            monitorService.exitQuiz(studentId);
            monitorService.clearResultViewing(studentId);
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        }
        return ResponseEntity.ok(ApiUtils.success(null));
    }

    /**
     * 문제풀이 화면이 주기적으로 호출해 이 세션이 아직 유효한지 확인한다(2026-08-26) — 다른 기기
     * 재로그인이나 직원 퇴실 처리로 세션이 무효화됐는데도 페이지 이동 전까지 계속 문제를 풀 수
     * 있던 문제를 막는다. requireSessionStudentId가 무효 판정 시 세션을 바로 invalidate한다.
     */
    @GetMapping("/student/session-check")
    @ResponseBody
    public ResponseEntity<?> sessionCheck(@RequestParam("studentId") String studentId, HttpServletRequest request) {
        boolean valid = requireSessionStudentId(request, studentId) != null;
        return ResponseEntity.ok(ApiUtils.success(Map.of("valid", valid)));
    }

    /**
     * PWA 설치 전용 랜딩 페이지
     */
    @GetMapping("/student/app/install")
    public String getInstallPage() {
        return "/student/app-install";
    }

    /** appId로 로그인 → 성공 시 메인 화면으로 이동 */
    @PostMapping("/student/login")
    public String login(@RequestParam("appId") String appId, RedirectAttributes redirectAttributes,
            HttpServletRequest request) {
        Student student = studentRepository.findByAppId(appId);
        if (student == null) {
            redirectAttributes.addFlashAttribute("error", "일치하는 학생 정보를 찾을 수 없어요. 다시 확인해주세요.");
            return "redirect:/student/login";
        }

        kioskService.assertSameCenter(request, student.getCenterCode());
        // 예약이 없을 경우 로그인 단계에서 막음.
        if (!reservationService.hasReservationToday(student.getStudentId())) {
            redirectAttributes.addFlashAttribute("noReservation", true);
            return "redirect:/student/login";
        }
        // 이용권이 없을 경우 로그인 단계에서 막음.
        if (passService.remain(student.getStudentId(),  "BOOK") <= 0) {
            redirectAttributes.addFlashAttribute("passExhausted", true);
            return "redirect:/student/login";
        }
        // 오늘 이미 퇴실했으면 문제풀이 기기에서 다시 입실/추천을 타지 않고 안내만 하고 막는다(2026-08-25).
        if (monitorService.hasExitedToday(student.getStudentId())) {
            redirectAttributes.addFlashAttribute("alreadyExited", true);
            return "redirect:/student/login";
        }
        // 오늘 입실(출석체크 기기) 기록이 없으면 입실부터 하라고 안내한다 — 문제풀이 기기(이 화면)는
        // 더 이상 자체적으로 입실/추천을 타지 않는다. PENDING 추천 유무가 아니라 "오늘 입실했는지"로
        // 판단한다(2026-08-25) — 책을 다 읽어 DONE 처리된 뒤라 PENDING이 없어도, 퇴실 전까지는
        // 로그아웃 후 재로그인해서 방금 읽던 책을 계속 볼 수 있어야 한다.
        if (!monitorService.hasEnteredToday(student.getStudentId())) {
            redirectAttributes.addFlashAttribute("notEntered", true);
            return "redirect:/student/login";
        }
        // appId로 신원을 확인한 이 시점에만 세션을 발급
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession newSession = request.getSession(true);
        newSession.setAttribute(SESSION_STUDENT_ID, student.getStudentId());
        // 이 학생의 "유효한" 세션을 이 기기 하나로 교체한다 — 다른 기기에 남아있던 이전 로그인은
        // 다음 요청부터 강제 로그아웃된다(2026-08-26, 중복 로그인 방지).
        studentSessionRegistry.register(student.getStudentId(), newSession.getId());
        // 실시간 모니터링 기준 "입실"은 로그인이 아니라 책 추천 시점(ClinicService.recommendBook)에 처리한다 
        // 예약 카드가 미입실 상태로 미리 떠 있다가 책을 추천받는 순간 입실로 전환된다.
        return "redirect:/student/main?studentId=" + student.getStudentId();
    }

    /**
     * 레벨 카드는 ClinicService.getMainLevelInfo로 완독 권수 기반 레벨/진행률을 계산해 내려준다.
     * 뱃지 패널은 카드 컬렉션 패널로 대체됐고, 카드 획득 기능이 미구현이라 아직 내려주는 데이터가 없다
     * (구현 시 여기서 cards 모델을 채우면 화면이 그대로 렌더링된다).
     * 추천 도서 카드는 페이지 진입 후 JS가 /clinic/recommend를 호출해 채운다 — 이미 추천받은 책이
     * 있으면 그 책 그대로, 없으면 새로 추천해서 대여까지 확정한다 (ClinicService.recommend가 멱등 처리).
     */
    @GetMapping("/student/main")
    public String getStudentMainPage(@RequestParam(value = "studentId", required = false) String studentId,
            Model model, HttpServletRequest request) {
        studentId = requireSessionStudentId(request, studentId);
        if (studentId == null) {
            return "redirect:/student/login";
        }

        Student student = studentRepository.findById(studentId);
        if (student == null) {
            return "redirect:/student/login";
        }

        // 결과 화면에서 "홈으로" 돌아온 시점 = 실시간 모니터링 기준 "결과 확인중" 종료
        // (재도전으로 문제풀이를 다시 시작하는 경우는 markQuizStarted가 함께 해제하므로 여기선 홈 복귀만 처리)
        monitorService.clearResultViewing(studentId);
        // 문제풀이 화면에서 제출 없이 "나가기"를 눌러 홈으로 온 경우 — "문제 푸는 중" 표시가 남아있지
        // 않도록 함께 해제한다(2026-08-26). 이미 제출을 거쳐 온 경우엔 어차피 지워져 있어 멱등이다.
        monitorService.exitQuiz(studentId);

        model.addAttribute("studentId", studentId);
        model.addAttribute("studentName", student.getStudentName());

        ClinicRespDTO.MainLevelInfoDTO levelInfo = clinicService.getMainLevelInfo(studentId);
        model.addAttribute("levelNo", levelInfo.getLevelNo());
        model.addAttribute("levelName", levelInfo.getLevelName());
        model.addAttribute("levelTitle", levelInfo.getTitle());
        model.addAttribute("feature", levelInfo.getFeature());
        model.addAttribute("characterImg", (String) null);
        model.addAttribute("booksToNextLevel", levelInfo.getBooksToNextLevel());
        model.addAttribute("progressPercent", levelInfo.getProgressPercent());
        model.addAttribute("monthBooks", clinicService.getMonthBooks(studentId));

        // 카드 컬렉션 패널은 획득 카드 수만큼 카드 이미지를 노출한다(진행도/책 정보는 표시 안 함)
        model.addAttribute("cards", clinicService.getCardCollection(studentId).getCards());
        return "/student/student-main";
    }

    /**
     * 문제풀이 화면 — 문제 데이터/도서 표지·제목은 화면 로드 후 JS에서
     * 기존 /question/search, /clinic/recommend API를 호출해 채운다
     */
    /** qlevel: 01=기본(기본값), 02=심화(독서왕/독서친구가 기본 문제풀이 합격 후 추가로 도전) */
    @GetMapping("/student/question")
    public String getQuestionPage(@RequestParam(value = "studentId", required = false) String studentId,
            @RequestParam(value = "contentId", required = false) Integer contentId,
            @RequestParam(value = "qlevel", required = false, defaultValue = "01") String qlevel,
            Model model, HttpServletRequest request) {
        studentId = requireSessionStudentId(request, studentId);
        if (studentId == null) {
            return "redirect:/student/login";
        }

        Student student = studentRepository.findById(studentId);
        if (student == null) {
            return "redirect:/student/login";
        }

        monitorService.markQuizStarted(studentId);

        // "나가기"를 눌렀을 때 어디로 보낼지 결정하는 모드 — 처음 풀기/재도전(불합격)/다시풀기(합격 후
        // 틀린 문제만) 세 가지를 구분한다(2026-08-25)
        String quizMode = contentId != null ? clinicService.getQuizMode(studentId, contentId, qlevel) : "FIRST";

        model.addAttribute("studentId", studentId);
        model.addAttribute("studentName", student.getStudentName());
        model.addAttribute("contentId", contentId);
        model.addAttribute("qlevel", qlevel);
        model.addAttribute("quizMode", quizMode);
        return "/student/student-question";
    }

    /**
     * 결과 화면 — 채점 결과(독서왕/독서친구/재도전)는 student-question.js가 채점 직후
     * sessionStorage에 담아 이 화면으로 넘긴다. 새로고침/직접 접근 등으로 결과가 없으면
     * student-result.js가 메인으로 되돌려보낸다.
     */
    @GetMapping("/student/result")
    public String getResultPage(@RequestParam(value = "studentId", required = false) String studentId,
            @RequestParam(value = "contentId", required = false) Integer contentId,
            @RequestParam(value = "qlevel", required = false, defaultValue = "01") String qlevel,
            Model model, HttpServletRequest request) {
        studentId = requireSessionStudentId(request, studentId);
        if (studentId == null) {
            return "redirect:/student/login";
        }

        Student student = studentRepository.findById(studentId);
        if (student == null) {
            return "redirect:/student/login";
        }

        // 결과 화면 진입 = 실시간 모니터링 기준 "결과 확인중" 시작 (홈으로/재도전 시 해제됨)
        monitorService.markResultViewing(studentId);
        // 재도전(불합격) 중 "나가기"로 직전 결과 화면에 온 경우도 "문제 푸는 중" 표시를 해제해야
        // 결과 확인중 상태가 제대로 보인다(2026-08-26). 제출을 거쳐 온 경우엔 이미 지워져 있어 멱등이다.
        monitorService.exitQuiz(studentId);

        model.addAttribute("studentId", studentId);
        model.addAttribute("studentName", student.getStudentName());
        model.addAttribute("contentId", contentId);
        model.addAttribute("qlevel", qlevel);
        return "/student/student-result";
    }

    // ── 출석체크 앱 ──────────────────────────────────────────────────────

    /**
     * 출석체크 홈. 기기 키가 없으면 입실/퇴실 대신 등록 안내를 보여준다 — 쿠키가 HttpOnly라
     * 화면 JS가 직접 못 읽으므로 서버가 판단해서 내려준다.
     *
     * 입실: QR 스캔 → /attendance/enter로 POST — 입실 처리 + 오늘 추천받은 책만 보여주는
     *      확인 화면으로 간다("닫기"를 누르면 이 출석체크 홈으로 복귀, 문제풀이로는 못 넘어간다 —
     *      출석체크 키오스크는 여러 학생이 돌려쓰는 공용 기기라 여기서 실제 학습까지 이어지면 안 된다).
     *      실제 학습(문제 풀기)은 학생 개인 폰의 문제풀이 앱(/student/login)에서 별도로 한다.
     * 퇴실: QR 스캔 → /student/exit 호출(로그인 컨텍스트 없이 studentId 생략) → 완료 메시지 후 이 화면으로 복귀
     */
    @GetMapping({ "/attendance", "/attendance/" })
    public String getAttendanceHomePage() {
        return "/student/attendance-home";
    }

    /**
     * QR 스캔으로 찾은 학생의 입실을 처리하고, 오늘 추천받은 책만 확인시켜주는 화면.
     * ClinicService.getHomeState가 내부적으로 enterSession까지 처리한다(기존 로그인 흐름과 동일).
     */
    @PostMapping("/attendance/enter")
    public String enterAttendance(@RequestParam("appId") String appId, Model model, HttpServletRequest request) {
        Student student = studentRepository.findByAppId(appId);
        if (student == null) {
            model.addAttribute("error", "일치하는 학생 정보를 찾을 수 없어요. QR을 다시 스캔해주세요.");
            return "/student/book-confirm";
        }
        // 이 기기의 센터 학생만 — 등록된 기기라는 것만으로 남의 센터 학생을 입실시킬 수는 없다
        kioskService.assertSameCenter(request, student.getCenterCode());

        // 예약 회차 시간대가 아니거나(ReservationService.markAttended), 예약이 아예 없거나, 이용권이
        // 소진된 경우 등은 입실 자체가 막혀야 하는 정상적인 업무 상황이지 시스템 오류가 아니다.
        // 예전엔 이 예외가 그대로 던져져 @RestControllerAdvice(GlobalExceptionHandler)가 가로채
        // 화면 전체에 순수 JSON({"success":false,...})이 그대로 보이는 문제가 있었다(2026-08-26,
        // 이 화면은 폼 제출로 들어오는 페이지 이동이라 fetch 에러 처리로 잡히지 않는다) — 여기서
        // 잡아서 학생을 못 찾은 경우와 같은 안내 카드(book-confirm.html의 error-card)로 보여준다.
        try {
            ClinicRespDTO.BookStatusRespDTO homeState = clinicService.getHomeState(student.getStudentId());
            model.addAttribute("studentName", student.getStudentName());
            model.addAttribute("book", homeState.getBook());
        } catch (Exception400 e) {
            model.addAttribute("error", e.getMessage());
        }
        return "/student/book-confirm";
    }

    // ── 학부모 앱 설치 랜딩 ──────────────────────────────────────────────

    /**
     * 학부모 앱(Flutter, 결제·이용권 조회용) 설치 랜딩 페이지.
     *
     * 위 학생 PWA 설치(/student/app/install)와 다르다 — 이건 브라우저에 설치되는 웹앱이 아니라
     * 진짜 안드로이드 네이티브 앱이라, beforeinstallprompt를 못 쓰고 .apk 파일을 그냥 내려받아
     * 안드로이드가 직접 설치하게 하는 것 말고는 방법이 없다(스토어 미게시,
     * static/apk/books_pay.apk를 이 서버가 직접 서빙).
     */
    @GetMapping("/app/install")
    public String getParentAppInstallPage() {
        return "/payment/app-install";
    }
}
