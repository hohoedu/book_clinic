package com.hohoedu.book_clinic._core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.hohoedu.book_clinic._core.interceptor.AppAdminReadOnlyInterceptor;
import com.hohoedu.book_clinic._core.interceptor.CommonInterceptor;
import com.hohoedu.book_clinic._core.interceptor.StudentSessionInterceptor;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final CommonInterceptor commonInterceptor;
    private final StudentSessionInterceptor studentSessionInterceptor;
    private final AppAdminReadOnlyInterceptor appAdminReadOnlyInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(commonInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/", "/login", "/join", "/signup", "/juso", "/jusoCallBack",
                        "/h2-console/**",
                        "/css/**", "/js/**", "/images/**",
                        "/favicon.ico"
                );
        registry.addInterceptor(studentSessionInterceptor)
                .addPathPatterns("/app/reservation/**", "/app/bookstore/**");
        // 앱 관리자 로그인 세션은 조회 전용 — 결제·환불·예약 변경 경로만 막는다(2026-10-21)
        registry.addInterceptor(appAdminReadOnlyInterceptor)
                .addPathPatterns(
                        "/app/reservation", "/app/reservation/cancel", "/app/reservation/batch",
                        "/payment/prepare", "/payment/prepare/**",
                        "/payment/refund",
                        "/payment/checkout", "/payment/checkout/**",
                        "/payment/billing/checkout", "/payment/billing/checkout/**",
                        "/payment/subscription/cancel", "/payment/subscription/retry");
    }
}
