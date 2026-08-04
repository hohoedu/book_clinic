package com.hohoedu.book_clinic._core.config;

import com.hohoedu.book_clinic._core.auth.CustomAuthenticationProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomAuthenticationProvider customAuthenticationProvider;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authenticationProvider(customAuthenticationProvider)
                .csrf(csrf -> csrf
                        // 세션/브라우저마다 다른 랜덤 토큰을 XSRF-TOKEN 쿠키로 내려준다(모든 사용자가
                        // 같은 고정 문자열을 공유하던 StaticCsrfTokenRepository를 대체, 2026-07-31).
                        // 쿠키 이름/헤더 이름(X-XSRF-TOKEN)은 Spring 기본값이라 기존 JS의
                        // CSRF_HEADER 상수와 그대로 맞는다.
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(eagerCsrfRequestHandler())
                        .ignoringRequestMatchers("/h2-console/**", "/login", "/question/upload",
                                "/api/notification/**", "/clinic/recommend", "/clinic/quiz/submit",
                                "/clinic/home-state", "/student/exit",
                                // 결제는 앱(Flutter)이 호출해서 CSRF 쿠키를 들고 다니지 않는다.
                                // 대신 PaymentController가 세션의 studentId와 요청의 studentId를 대조한다.
                                "/payment/**",
                                // 학부모 앱도 쿠키 기반 CSRF 토큰을 들고 다니지 않는다
                                "/app/**", "/pass/**"))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/login", "/join",
                                "/error",
                                "/h2-console/**",
                                "/css/**", "/js/**", "/images/**", "/uploads/**", "/favicon.ico",
                                "/manifest.json", "/sw.js",
                                "/launch",
                                "/temp-upload.html",
                                "/question/upload/template",
                                "/question/upload",
                                "/question/search",
                                "/clinic/recommend",
                                "/clinic/quiz/submit",
                                "/clinic/home-state",
                                "/student/**",
                                "/attendance/**",
                                // 직원 로그인이 아니라 학생 세션으로 접근하는 API라 여기서 열고,
                                // 본인 확인은 PaymentController가 세션 studentId 대조로 한다
                                "/payment/**",
                                // 학부모 앱 로그인. 인증 자체를 하는 곳이라 여기서 열어야 한다
                                "/app/**",
                                // 이용권 잔여 조회. 본인 확인은 PassController가 세션으로 한다
                                "/pass/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        // 로그인 성공 시 저장된 요청(saved-request) 무시하고 항상 도서 데이터 화면으로 이동
                        .defaultSuccessUrl("/admin/book-data", true)
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/login")
                        .permitAll());

        return http.build();
    }

    /**
     * Security 6는 CSRF 토큰을 지연(deferred) 로딩해서, 요청 중 아무도 토큰을 읽지 않으면
     * XSRF-TOKEN 쿠키를 아예 내려주지 않는다. 특히 로그인 성공 시점에 기존 쿠키를 지운 뒤
     * 새 토큰은 "읽힐 때" 저장되므로, 화면이 토큰을 렌더링하지 않는 이 프로젝트에서는
     * 로그인 후 쿠키가 사라져 JS의 X-XSRF-TOKEN 헤더가 빈 값이 되고 POST가 전부 403이 된다.
     * 요청 attribute 이름을 null로 두면 매 요청마다 토큰을 즉시 로딩 → 쿠키가 항상 발급된다.
     */
    private CsrfTokenRequestAttributeHandler eagerCsrfRequestHandler() {
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }
}
