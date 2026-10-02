package com.hohoedu.book_clinic._core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.hohoedu.book_clinic._core.interceptor.CommonInterceptor;
import com.hohoedu.book_clinic._core.interceptor.StudentSessionInterceptor;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final CommonInterceptor commonInterceptor;
    private final StudentSessionInterceptor studentSessionInterceptor;

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
    }
}
