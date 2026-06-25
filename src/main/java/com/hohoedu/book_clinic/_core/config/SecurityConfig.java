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
                        .ignoringRequestMatchers("/h2-console/**", "/login", "/question/upload"))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/login", "/join",
                                "/error",
                                "/h2-console/**",
                                "/css/**", "/js/**", "/images/**", "/favicon.ico",
                                "/temp-upload.html",
                                "/question/upload/template",
                                "/question/upload")
                        .permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .defaultSuccessUrl("/api/user/me")
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/login")
                        .permitAll());

        return http.build();
    }
}
