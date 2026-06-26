package com.hohoedu.book_clinic._core.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import jakarta.annotation.PostConstruct;
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
}
