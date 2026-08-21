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
                                                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                                                .csrfTokenRequestHandler(eagerCsrfRequestHandler())
                                                .ignoringRequestMatchers("/h2-console/**", "/login", "/question/upload",
                                                                "/api/notification/**", "/clinic/recommend",
                                                                "/clinic/quiz/submit",
                                                                "/clinic/home-state", "/student/exit",
                                                                "/payment/**",
                                                                "/app/**", "/pass/**", "/test/**"))
                                .headers(headers -> headers
                                                .frameOptions(frame -> frame.sameOrigin()))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(
                                                                "/login", "/join","/test/**",
                                                                "/error",
                                                                "/h2-console/**",
                                                                "/css/**", "/js/**", "/images/**", "/uploads/**",
                                                                "/favicon.ico",
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
                                                                "/payment/**",
                                                                "/app/**",
                                                                "/pass/**",
                                                                "/sso/callback",
                                                                "/kiosk/register")
                                                .permitAll()
                                                .anyRequest().authenticated())
                                .formLogin(form -> form
                                                // 세션 만료 등으로 튕겨나간 경우 원래 요청했던 페이지로 복귀, 그 외(예: /login 직접 접속)는 도서 데이터 화면으로 이동
                                                .defaultSuccessUrl("/admin/book-data", false)
                                                .permitAll())
                                .logout(logout -> logout
                                                .logoutSuccessUrl("/login")
                                                .permitAll());

                return http.build();
        }

        private CsrfTokenRequestAttributeHandler eagerCsrfRequestHandler() {
                CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
                handler.setCsrfRequestAttributeName(null);
                return handler;
        }
}
