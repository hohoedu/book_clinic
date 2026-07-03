package com.hohoedu.book_clinic._core.config;

import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.hohoedu.book_clinic._core.interceptor.CommonInterceptor;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final CommonInterceptor commonInterceptor;

    /** 업로드 파일 저장 디렉터리 (기본: 실행 위치 기준 uploads) */
    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(commonInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/", "/login", "/join",
                        "/h2-console/**",
                        "/css/**", "/js/**", "/images/**", "/uploads/**",
                        "/favicon.ico"
                );
    }

    /** 업로드된 도서 이미지를 /uploads/** URL로 정적 제공 */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir).toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }
}
