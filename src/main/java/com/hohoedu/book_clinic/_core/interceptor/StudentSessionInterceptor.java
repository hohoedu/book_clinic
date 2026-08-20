package com.hohoedu.book_clinic._core.interceptor;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.hohoedu.book_clinic._core.handler.exception.Exception401;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 학생 앱 예약 API의 로그인 확인 (2026-08-20).
 *
 * [왜 컨트롤러가 아니라 인터셉터인가] 세션 확인이 컨트롤러 메서드 안에만 있으면, 요청 본문을
 * 객체로 바꾸는 작업이 메서드 호출보다 먼저 일어나기 때문에 비로그인 요청이라도 본문이 조금
 * 이상하면 401이 아니라 400("요청 형식이 올바르지 않습니다")으로 응답한다. 로그인하지 않은
 * 사람에게 형식 오류를 알려주는 셈이라 원인을 오해하게 만든다. 본문을 읽기 전에 끊는다.
 *
 * 컨트롤러의 requireStudentId는 그대로 남겨둔다 — 경로 패턴이 바뀌어 이 인터셉터가 비켜가도
 * 인증이 통째로 빠지지 않도록 하는 이중 방어다.
 */
@Component
public class StudentSessionInterceptor implements HandlerInterceptor {

    private static final String SESSION_STUDENT_ID = "studentId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SESSION_STUDENT_ID) == null) {
            throw new Exception401("로그인이 필요합니다.");
        }
        return true;
    }
}
