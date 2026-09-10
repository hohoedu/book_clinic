package com.hohoedu.book_clinic.app;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hohoedu.book_clinic._core.handler.exception.Exception401;
import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic.app._dto.BookstoreMainRespDTO;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * i-with(학부모 Flutter 앱) 화면 조립용 조회 API.
 *
 * 인증은 {@link AppAuthController} 가 만든 세션(studentId)을 그대로 쓴다. studentId 는
 * 항상 세션에서만 가져오고 요청 본문으로는 받지 않는다 — 다른 학생 데이터를 대신 조회하는
 * 경로를 원천 차단하기 위해서다. 로직은 담지 않고 {@link AppRepository} 조회만 위임한다.
 */
@Slf4j
@RestController
@RequestMapping("/app")
@RequiredArgsConstructor
public class AppController {

    private static final String SESSION_STUDENT_ID = "studentId";

    private final AppRepository appRepository;

    /** 책방 메인 화면 — 다음 예약 / 이용권 잔여 / 직전 이용 / 최근 독서기록. */
    @PostMapping("/bookstore/main")
    public ResponseEntity<?> bookstoreMain(HttpServletRequest request) {
        String studentId = requireStudentId(request);
        BookstoreMainRespDTO res = appRepository.selectBookstoreMain(studentId);
        return ResponseEntity.ok(ApiUtils.success(res));
    }

    private String requireStudentId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object studentId = session == null ? null : session.getAttribute(SESSION_STUDENT_ID);
        if (studentId == null) {
            throw new Exception401("로그인이 필요합니다.");
        }
        return studentId.toString();
    }
}
