package com.hohoedu.book_clinic.common.notification;

import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Firebase Cloud Messaging(FCM) 발송 서비스
 * 단건 발송 및 멀티캐스트(일괄) 발송 기능 제공
 */
@Slf4j
@Service
public class FcmService {

    /**
     * 단일 FCM 토큰으로 푸시 알림 발송
     *
     * @param token FCM 디바이스 토큰
     * @param title 알림 제목
     * @param body  알림 내용
     * @return 발송 성공 여부
     */
    public boolean sendToToken(String token, String title, String body) {
        Message message = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .build();
        try {
            FirebaseMessaging.getInstance().send(message);
            return true;
        } catch (FirebaseMessagingException e) {
            log.warn("FCM 단건 발송 실패 token={} error={}", token, e.getMessage());
            return false;
        }
    }

    /**
     * 여러 FCM 토큰으로 멀티캐스트 발송 (FCM 제한: 최대 500개/회 자동 분할)
     *
     * @param tokens FCM 디바이스 토큰 목록
     * @param title  알림 제목
     * @param body   알림 내용
     * @return int[]{successCount, failCount}
     */
    public int[] sendMulticast(List<String> tokens, String title, String body) {
        int successCount = 0;
        int failCount = 0;

        List<List<String>> batches = partition(tokens, 500);
        for (List<String> batch : batches) {
            MulticastMessage message = MulticastMessage.builder()
                    .addAllTokens(batch)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .build();
            try {
                BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
                successCount += response.getSuccessCount();
                failCount += response.getFailureCount();
            } catch (FirebaseMessagingException e) {
                log.error("FCM 멀티캐스트 발송 실패 batchSize={} error={}", batch.size(), e.getMessage());
                failCount += batch.size();
            }
        }
        return new int[]{successCount, failCount};
    }

    /**
     * 리스트를 지정한 크기로 분할
     */
    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }
}
