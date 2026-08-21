package com.hohoedu.book_clinic._core.config;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.WriteBatch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * dev 프로필 전용 — 서버 기동 시(schema.sql이 SQL을 이미 초기화한 뒤) Firestore
 * clinic_monitor 컬렉션도 함께 비운다.
 *
 * schema.sql은 spring.sql.init.mode=always라 재기동할 때마다 세션/추천 등 관련 테이블을 통째로
 * DROP/CREATE하는데, 이건 SQL만 아는 리셋이라 Firestore(모니터링 카드 미러) 문서는 그대로 남는다.
 * 그 상태로 실시간 모니터링 화면을 열면 REST 초기 조회는 빈 SQL 기준으로 정상 비어있게 나오지만,
 * 곧이어 Firestore 구독이 "기존 데이터"로 그 유령 문서들을 다시 밀어넣어 화면에 되살아난다
 * (2026-07-23 발견). 프로덕션은 테이블을 이런 식으로 DROP하지 않으므로 이 문제가 생기지 않는다 —
 * 그래서 dev 프로필에서만 SQL 리셋 직후 Firestore도 맞춰서 비운다.
 */
@Slf4j
@Component
@Profile("dev")
@Order
@RequiredArgsConstructor
public class DevFirestoreResetRunner implements ApplicationRunner {

    private static final String COLLECTION = "clinic_monitor";

    private final Firestore firestore;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<QueryDocumentSnapshot> docs = firestore.collection(COLLECTION).get().get().getDocuments();
        if (docs.isEmpty()) return;

        WriteBatch batch = firestore.batch();
        for (DocumentSnapshot doc : docs) {
            batch.delete(doc.getReference());
        }
        batch.commit().get();
        log.info("[dev] SQL 초기화에 맞춰 Firestore {} 문서 {}건을 함께 정리했습니다.", COLLECTION, docs.size());
    }

}
