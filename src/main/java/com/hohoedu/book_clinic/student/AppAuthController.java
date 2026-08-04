package com.hohoedu.book_clinic.student;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hohoedu.book_clinic._core.handler.exception.Exception401;
import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic._core.utils.HashUtils;
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
 */
@Slf4j
@RestController
@RequestMapping("/app")
@RequiredArgsConstructor
public class AppAuthController {

    private static final String SESSION_STUDENT_ID = "studentId";

    private final StudentRepository studentRepository;

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

        // 같은 기기를 여러 계정이 돌려쓸 수 있으므로 기존 세션은 버리고 새로 발급한다.
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        request.getSession(true).setAttribute(SESSION_STUDENT_ID, student.getStudentId());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("studentId", student.getStudentId());
        response.put("studentName", student.getStudentName());
        response.put("centerCode", student.getCenterCode());
        return ResponseEntity.ok(ApiUtils.success(response));
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
