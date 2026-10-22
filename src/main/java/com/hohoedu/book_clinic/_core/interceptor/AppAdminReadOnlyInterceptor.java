package com.hohoedu.book_clinic._core.interceptor;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.hohoedu.book_clinic._core.handler.exception.Exception403;
import com.hohoedu.book_clinic.app.AppAuthController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 앱 관리자 로그인 세션은 조회만 허용한다 (2026-10-21).
 *
 * 직원이 학생 계정으로 들어가(AppAuthController.adminLogin) 화면을 확인하는 용도라, 학부모 대신
 * 결제·환불하거나 예약을 바꾸는 경로는 막는다. 막을 경로 목록은 WebConfig 가 이 인터셉터에 건다 —
 * 여기서는 세션에 관리자 표시가 있는지만 본다. 일반 로그인 세션은 그대로 통과한다.
 */
@Component
public class AppAdminReadOnlyInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        if (session != null && Boolean.TRUE.equals(session.getAttribute(AppAuthController.SESSION_APP_ADMIN))) {
            throw new Exception403("관리자 로그인에서는 조회만 할 수 있습니다.");
        }
        return true;
    }
}
