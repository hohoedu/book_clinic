package com.hohoedu.book_clinic._core.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;

/**
 * Firebase Admin SDK 초기화 설정
 * 애플리케이션 기동 시 서비스 계정 키로 FirebaseApp 인스턴스를 생성
 */
@Configuration
public class FcmConfig {

    @Value("${FIREBASE_CREDENTIALS_PATH}")
    private Resource credentialsResource;

    /**
     * Firebase 초기화 - 중복 초기화 방지를 위해 getApps() 체크 후 실행
     */
    @PostConstruct
    public void init() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentialsResource.getInputStream()))
                    .build();
            FirebaseApp.initializeApp(options);
        }
    }

    /**
     * FirebaseApp은 JVM 전역 static 싱글턴이라 Spring Boot DevTools 핫 리스타트로 컨텍스트만
     * 새로 떠도 이 인스턴스는 그대로 남는다. 문제는 이전 컨텍스트가 내려갈 때 그 안에서 쓰던
     * Firestore 클라이언트가 함께 닫혀버린다는 것 — 그 상태로 새 컨텍스트가 같은(이미 닫힌)
     * FirebaseApp을 재사용하면 이후 모든 Firestore 쓰기가 "client has already been closed"로
     * 조용히 실패한다(MonitorService.syncSafely가 예외를 삼켜서 SQL은 정상 저장되지만 실시간
     * 모니터링 화면엔 영원히 반영이 안 됨, 2026-07-23 발견). 컨텍스트 종료 시 명시적으로 정리해서
     * 다음 리스타트 때 init()이 깨끗하게 새로 초기화하도록 한다.
     */
    @PreDestroy
    public void destroy() {
        FirebaseApp.getApps().forEach(FirebaseApp::delete);
    }
}
