package com.hohoedu.book_clinic._core.view;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.handler.exception.Exception404;
import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic.clinic.ClinicService;
import com.hohoedu.book_clinic.clinic._dto.ClinicRespDTO;
import com.hohoedu.book_clinic.monitor.MonitorService;
import com.hohoedu.book_clinic.pass.PassService;
import com.hohoedu.book_clinic.student.StudentRepository;
import com.hohoedu.book_clinic.student.model.Student;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * 학생 화면 라우팅 — 2026-07-09 클리닉 로직(책 추천/문제풀이/채점) 전체 재설계를 위해
 * ClinicService 연동 부분은 임시로 제거된 상태. 1) 책 추천부터 단계별로 다시 연결한다.
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
@RequestMapping("/student")
public class StudentViewController {

    private static final String SESSION_STUDENT_ID = "studentId";

    private final StudentRepository studentRepository;
    private final ClinicService clinicService;
    private final MonitorService monitorService;
    private final PassService passService;

    /**
     * 세션에 로그인된 studentId가 없으면(로그인 안 함/세션 만료) null. 세션은 있는데 URL의
     * studentId가 다른 학생을 가리키면(다른 학생 화면을 직접 열어보려는 시도) 역시 null —
     * 세션값이 항상 우선이고, URL 파라미터는 그것과 일치할 때만(또는 비어있을 때만) 유효하다.
     */
    private String requireSessionStudentId(HttpServletRequest request, String requestedStudentId) {
        HttpSession session = request.getSession(false);
        Object sessionStudentId = session == null ? null : session.getAttribute(SESSION_STUDENT_ID);
        if (sessionStudentId == null) {
            return null;
        }
        if (requestedStudentId != null && !requestedStudentId.isBlank() && !sessionStudentId.equals(requestedStudentId)) {
            return null;
        }
        return (String) sessionStudentId;
    }

    /** 학생 로그인 화면 — 실제 QR 스캔 대신 appId를 직접 입력해서 테스트하는 임시 화면 */
    @GetMapping({ "", "/", "/login" })
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
    @PostMapping("/exit")
    @ResponseBody
    public ResponseEntity<?> exitByQr(@RequestBody Map<String, String> body) {
        String studentId = body.get("studentId");
        String appId = body.get("appId");
        Student student = studentRepository.findByAppId(appId);
        if (student == null) {
            throw new Exception404("일치하는 학생 정보를 찾을 수 없어요. QR을 다시 스캔해주세요.");
        }
        if (studentId != null && !studentId.isBlank() && !student.getStudentId().equals(studentId)) {
            throw new Exception400("본인 학생증 QR이 아니에요. 다시 확인해주세요.");
        }
        monitorService.exitSession(student.getStudentId());
        return ResponseEntity.ok(ApiUtils.success(null));
    }

    /**
     * PWA 설치 전용 랜딩 페이지 — 직원에게 "이 주소 열고 설치 눌러"로 안내하기 위한 화면.
     * 브라우저 정책상 URL 접속만으로 자동 설치는 불가(prompt는 사용자 클릭 제스처 필요)라,
     * beforeinstallprompt를 잡아 큰 "설치하기" 버튼 한 번으로 설치되게 한다. iOS는 이벤트 자체가
     * 없어 "공유 → 홈 화면에 추가" 안내로 분기, 이미 설치된 경우도 별도 안내.
     */
    @GetMapping("/app/install")
    public String getInstallPage() {
        return "/student/app-install";
    }

    /** appId로 로그인 → 성공 시 메인 화면으로 이동 */
    @PostMapping("/login")
    public String login(@RequestParam("appId") String appId, RedirectAttributes redirectAttributes,
            HttpServletRequest request) {
        Student student = studentRepository.findByAppId(appId);
        if (student == null) {
            redirectAttributes.addFlashAttribute("error", "일치하는 학생 정보를 찾을 수 없어요. 다시 확인해주세요.");
            return "redirect:/student/login";
        }
        // 이용권이 없으면 메인 화면까지 들여보내지 않고 로그인 단계에서 바로 막는다(2026-08-05).
        // 세션을 만들기 전에 확인하므로 여기서 막힌 학생은 홈 화면 URL을 직접 열어도 재로그인부터
        // 다시 해야 한다. MonitorService.enterSession()에도 같은 조건의 차단이 남아있는데, 그건
        // 로그인 이후 세션 중간에 이용권이 회수되는 것 같은 드문 경우를 막는 이중 방어용이다.
        if (passService.remain(student.getStudentId(), "BOOK") <= 0) {
            redirectAttributes.addFlashAttribute("passExhausted", true);
            return "redirect:/student/login";
        }
        // QR(appId)로 신원을 확인한 이 시점에만 세션을 발급한다 — 이후 화면들은 URL의 studentId가
        // 아니라 이 세션값을 신뢰한다. 기존 세션에 다른 학생이 남아있을 수 있으니(같은 브라우저를
        // 여러 학생이 돌려쓰는 키오스크 환경) invalidate 후 새로 발급한다.
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        request.getSession(true).setAttribute(SESSION_STUDENT_ID, student.getStudentId());
        // 실시간 모니터링 기준 "입실"은 로그인이 아니라 책 추천 시점(ClinicService.recommendBook)에
        // 처리한다 — 예약 카드가 미입실 상태로 미리 떠 있다가 책을 추천받는 순간 입실로 전환된다.
        return "redirect:/student/main?studentId=" + student.getStudentId();
    }

    /**
     * 레벨 카드는 ClinicService.getMainLevelInfo로 완독 권수 기반 레벨/진행률을 계산해 내려준다.
     * 뱃지 패널은 카드 컬렉션 패널로 대체됐고, 카드 획득 기능이 미구현이라 아직 내려주는 데이터가 없다
     * (구현 시 여기서 cards 모델을 채우면 화면이 그대로 렌더링된다).
     * 추천 도서 카드는 페이지 진입 후 JS가 /clinic/recommend를 호출해 채운다 — 이미 추천받은 책이
     * 있으면 그 책 그대로, 없으면 새로 추천해서 대여까지 확정한다 (ClinicService.recommend가 멱등 처리).
     */
    @GetMapping("/main")
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
    @GetMapping("/question")
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

        // 문제풀이 화면 진입 = 실시간 모니터링 기준 "문제 푸는 중" 시작 (제출하면 해제됨)
        monitorService.markQuizStarted(studentId);

        model.addAttribute("studentId", studentId);
        model.addAttribute("studentName", student.getStudentName());
        model.addAttribute("contentId", contentId);
        model.addAttribute("qlevel", qlevel);
        return "/student/student-question";
    }

    /**
     * 결과 화면 — 채점 결과(독서왕/독서친구/재도전)는 student-question.js가 채점 직후
     * sessionStorage에 담아 이 화면으로 넘긴다. 새로고침/직접 접근 등으로 결과가 없으면
     * student-result.js가 메인으로 되돌려보낸다.
     */
    @GetMapping("/result")
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

        model.addAttribute("studentId", studentId);
        model.addAttribute("studentName", student.getStudentName());
        model.addAttribute("contentId", contentId);
        model.addAttribute("qlevel", qlevel);
        return "/student/student-result";
    }

}
