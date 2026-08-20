package com.hohoedu.book_clinic._core.config;

import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.hohoedu.book_clinic._core.interceptor.CommonInterceptor;
import com.hohoedu.book_clinic._core.interceptor.KioskTokenInterceptor;
import com.hohoedu.book_clinic._core.interceptor.StudentSessionInterceptor;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final CommonInterceptor commonInterceptor;
    private final StudentSessionInterceptor studentSessionInterceptor;
    private final KioskTokenInterceptor kioskTokenInterceptor;

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

        // 학생 앱 예약 API만 — /app/login, /app/session은 세션이 없는 상태로 들어오는 게 정상이라 제외된다
        registry.addInterceptor(studentSessionInterceptor)
                .addPathPatterns("/app/reservation/**");

        // 학생용 쓰기 요청은 등록된 센터 기기에서만 — 인터셉터가 GET은 스스로 통과시키므로
        // 화면(로그인 페이지, 기기 등록 안내)은 등록 전에도 열린다.
        // 학부모 앱(/app, /payment)은 개인 폰에서 비밀번호로 인증하므로 대상이 아니다.
        registry.addInterceptor(kioskTokenInterceptor)
                .addPathPatterns("/student/**", "/attendance/**", "/clinic/**");
    }

    /** 업로드된 도서 이미지를 /uploads/** URL로 정적 제공 */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDir).toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }
}
