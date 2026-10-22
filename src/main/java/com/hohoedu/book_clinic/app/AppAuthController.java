package com.hohoedu.book_clinic.app;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.handler.exception.Exception401;
import com.hohoedu.book_clinic._core.handler.exception.Exception403;
import com.hohoedu.book_clinic._core.handler.exception.Exception404;
import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic._core.utils.HashUtils;
import com.hohoedu.book_clinic.student.StudentRepository;
import com.hohoedu.book_clinic.student.model.Student;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 학부모 Flutter 앱 로그인 API.
 *
 * [왜 따로 있나] 학생은 PWA에서 QR(appId)만으로 로그인한다 — 키오스크 앞에서 QR을 찍는
 * 상황이라 비밀번호를 물을 수 없기 때문이다(StudentViewController.login). 반면 학부모 앱은
 * 결제와 결제내역을 다루므로 QR 없이 아이디·비밀번호로 신원을 확인해야 한다.
 *
 * [계정] 학부모는 자녀의 앱 계정(app_id/app_password)을 그대로 쓴다(2026-08-04 확정).
 * 별도 학부모 계정 체계를 만들지 않으므로 자녀가 둘이면 계정도 둘이다.
 *
 * [세션] 토큰이 아니라 기존 HttpSession을 그대로 쓴다. PaymentController를 비롯한 서버
 * 전체가 세션의 studentId를 신뢰하는 구조라, 앱만 토큰을 쓰면 인증 경로가 두 개가 된다.
 * Flutter 쪽에서는 쿠키를 유지하는 HTTP 클라이언트(dio + cookie_jar 등)를 쓰면 된다.
 *
 * [형제·관리자 (2026-10-21)] 앱은 로그인한 계정 하나로 형제를 골라 보고, 직원은 비밀번호 없이
 * 학생 계정으로 들어가 화면을 확인한다(all_pass /login_skip). 책방도 같은 흐름이 되도록
 * 형제 전환(/sibling/switch)과 관리자 로그인(/login/admin)을 둔다. 관리자 세션은 조회만
 * 허용한다 — 결제·환불·예약 변경은 {@link com.hohoedu.book_clinic._core.interceptor.AppAdminReadOnlyInterceptor}가 막는다.
 */
@Slf4j
@RestController
@RequestMapping("/app")
@RequiredArgsConstructor
public class AppAuthController {

    private static final String SESSION_STUDENT_ID = "studentId";
    /** 관리자 로그인 세션 표시 — AppAdminReadOnlyInterceptor 가 같은 키를 본다 */
    public static final String SESSION_APP_ADMIN = "appAdmin";

    private final StudentRepository studentRepository;

    /**
     * 앱 관리자 로그인 확인값 — 앱이 직원용 비밀번호(all_pass 와 같은 값)를 그대로 보낸다.
     * 비워두면 관리자 로그인이 꺼진다.
     */
    @Value("${app.admin-login.password:}")
    private String adminLoginPassword;

    /**
     * 앱 로그인 — appId + password.
     *
     * 아이디가 없는 경우와 비밀번호가 틀린 경우를 같은 메시지로 응답한다.
     * 메시지가 다르면 "이 아이디는 존재한다"는 사실이 새어나가 계정 추측에 쓰인다.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String appId = body.get("appId");
        String password = body.get("password");

        if (appId == null || appId.isBlank() || password == null || password.isBlank()) {
            throw new Exception401("아이디와 비밀번호를 입력해주세요.");
        }

        Student student = studentRepository.findByAppId(appId);
        // erp_student에는 salt 컬럼이 없어 salt 없이 해시된 값이 저장돼 있다(HashUtils 참고).
        if (student == null || !HashUtils.hashPassword(password, null).equals(student.getAppPassword())) {
            throw new Exception401("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        return ResponseEntity.ok(ApiUtils.success(startSession(request, student, false)));
    }

    /**
     * 앱 관리자 로그인 — appId + 직원용 비밀번호(2026-10-21).
     *
     * all_pass /login_skip 처럼 학생 비밀번호 없이 그 학생으로 들어간다. 대신 세션에 관리자 표시를
     * 남겨 조회만 하게 한다 — 직원이 화면을 확인하다가 학부모 카드로 결제·환불하거나 예약을
     * 바꾸면 안 되기 때문이다.
     */
    @PostMapping("/login/admin")
    public ResponseEntity<?> adminLogin(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String appId = body.get("appId");
        String password = body.get("password");

        if (adminLoginPassword == null || adminLoginPassword.isBlank()) {
            throw new Exception403("관리자 로그인을 사용할 수 없습니다.");
        }
        if (appId == null || appId.isBlank() || !adminLoginPassword.equals(password)) {
            throw new Exception401("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        Student student = studentRepository.findByAppId(appId);
        if (student == null) {
            throw new Exception401("아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        log.info("앱 관리자 로그인 appId={}, studentId={}", appId, student.getStudentId());
        return ResponseEntity.ok(ApiUtils.success(startSession(request, student, true)));
    }

    /**
     * 형제 전환 — 로그인한 세션의 학생을 같은 형제 그룹의 다른 학생으로 바꾼다(2026-10-21).
     *
     * 앱은 계정 하나로 로그인한 뒤 형제 목록에서 자녀를 고른다. 형제 계정의 비밀번호는 앱이 모르므로
     * 다시 로그인하지 않고, 지금 세션이 이미 그 형제 그룹의 학생으로 인증돼 있다는 사실로 전환을 허용한다.
     * 관리자 세션이면 관리자 표시도 그대로 유지한다(조회 전용이 풀리지 않게).
     */
    @PostMapping("/sibling/switch")
    public ResponseEntity<?> switchSibling(@RequestBody Map<String, String> body, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object current = session == null ? null : session.getAttribute(SESSION_STUDENT_ID);
        if (current == null) {
            throw new Exception401("로그인이 필요합니다.");
        }

        String targetStudentId = body.get("studentId");
        if (targetStudentId == null || targetStudentId.isBlank()) {
            throw new Exception400("studentId 가 필요합니다.");
        }

        String currentStudentId = (String) current;
        if (!currentStudentId.equals(targetStudentId)
                && !studentRepository.isSameSiblingGroup(currentStudentId, targetStudentId)) {
            throw new Exception403("형제로 등록된 학생만 선택할 수 있습니다.");
        }

        Student target = studentRepository.findById(targetStudentId);
        if (target == null) {
            throw new Exception404("학생 정보를 찾을 수 없습니다.");
        }

        boolean admin = Boolean.TRUE.equals(session.getAttribute(SESSION_APP_ADMIN));
        return ResponseEntity.ok(ApiUtils.success(startSession(request, target, admin)));
    }

    /**
     * 세션 발급 — 같은 기기를 여러 계정이 돌려쓸 수 있으므로 기존 세션은 버리고 새로 만든다.
     * 형제 전환도 새로 발급해 이전 학생 기준으로 남아 있던 세션 값이 섞이지 않게 한다.
     */
    private Map<String, Object> startSession(HttpServletRequest request, Student student, boolean admin) {
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_STUDENT_ID, student.getStudentId());
        if (admin) {
            session.setAttribute(SESSION_APP_ADMIN, Boolean.TRUE);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("studentId", student.getStudentId());
        response.put("studentName", student.getStudentName());
        response.put("centerCode", student.getCenterCode());
        response.put("admin", admin);
        return response;
    }

    /** 앱 로그아웃 */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok(ApiUtils.success("로그아웃되었습니다."));
    }

    /**
     * 세션 확인 — 앱이 켜질 때 자동 로그인 여부를 판단하는 용도.
     * 세션이 없으면 401이 떨어지므로 앱은 로그인 화면을 띄우면 된다.
     */
    @PostMapping("/session")
    public ResponseEntity<?> session(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object studentId = session == null ? null : session.getAttribute(SESSION_STUDENT_ID);
        if (studentId == null) {
            throw new Exception401("로그인이 필요합니다.");
        }
        return ResponseEntity.ok(ApiUtils.success(Map.of("studentId", studentId)));
    }
}
