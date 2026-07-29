package com.hohoedu.book_clinic._core.config;

import com.hohoedu.book_clinic._core.auth.CustomAuthenticationProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
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
                        .csrfTokenRepository(new StaticCsrfTokenRepository())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers("/h2-console/**", "/login", "/question/upload",
                                "/api/notification/**", "/clinic/recommend", "/clinic/quiz/submit",
                                "/clinic/home-state"))
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
                                "/temp-upload.html",
                                "/question/upload/template",
                                "/question/upload",
                                "/question/search",
                                "/clinic/recommend",
                                "/clinic/quiz/submit",
                                "/clinic/home-state",
                                "/student/**")
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
}
