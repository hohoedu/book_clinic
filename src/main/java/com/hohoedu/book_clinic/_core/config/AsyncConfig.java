package com.hohoedu.book_clinic._core.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Firestore 동기화 전용 스레드 풀 (2026-08-26).
 *
 * MonitorSyncService.syncCard가 이 풀에서 실행되도록 @Async("firestoreSyncExecutor")로 지정된다.
 * 원래는 학생 입실/퇴실/문제풀이 진입 등 @Transactional 메서드 끝에서 Firestore 쓰기를 동기(.get())로
 * 기다렸는데, 그동안 DB 커넥션을 계속 붙잡고 있어서 Firestore 왕복이 지연되면 커넥션 풀이 고갈되어
 * 앱 전체가 느려지는 문제가 있었다. 전용 풀로 분리해 별도 스레드에서 처리하면, DB 트랜잭션은
 * Firestore 응답을 기다리지 않고 바로 커넥션을 반납하고 사용자 응답도 즉시 나간다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "firestoreSyncExecutor")
    public Executor firestoreSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("firestore-sync-");
        executor.initialize();
        return executor;
    }
}
