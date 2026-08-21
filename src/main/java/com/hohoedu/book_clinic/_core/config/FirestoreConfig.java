package com.hohoedu.book_clinic._core.config;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.cloud.FirestoreClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

/**
 * Firestore 클라이언트 빈 — 신규 의존성 없이 firebase-admin(FcmConfig가 초기화한 FirebaseApp) 안에 이미 포함된 Firestore 클라이언트를 재사용한다. 
 * 실시간 모니터링(monitor 패키지)이 SQL을 원본으로 두고 카드 상태 변화를 이 빈으로 Firestore에 미러링한다 (2026-07-15).
 */
@Configuration
public class FirestoreConfig {

    @Bean
    @DependsOn("fcmConfig")
    public Firestore firestore() {
        return FirestoreClient.getFirestore(FirebaseApp.getInstance());
    }
}
