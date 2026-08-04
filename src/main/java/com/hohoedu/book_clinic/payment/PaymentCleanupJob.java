package com.hohoedu.book_clinic.payment;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.hohoedu.book_clinic._core.utils.KstClock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * READY로 방치된 결제를 주기적으로 정리한다.
 *
 * [왜 필요한가] 결제창을 닫을 때 앱이 /payment/abandon을 호출하고, 이니시스 페이지 안에서
 * 취소해도 서버가 결과를 받아 정리하지만, 둘 다 "신호가 서버까지 도달해야" 동작한다.
 * 사용자가 앱을 강제 종료하거나 네트워크가 끊긴 채 나가면 그 신호 자체가 오지 않는다.
 * 그런 경우까지 잡는 마지막 방어선이라 클라이언트 신호와 무관하게 시간으로만 판단한다.
 *
 * [기준 시간] 승인 흐름(결제창 인증 → 서버 승인)이 정상적으로는 몇 분 안에 끝나므로,
 * 10분이 넘도록 READY인 건은 사실상 이탈로 본다. 실제 결제 흐름이 이보다 오래 걸릴
 * 사정이 생기면(예: 카드사 앱 인증이 오래 걸림) 이 값을 늘려야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCleanupJob {

    private static final int STALE_MINUTES = 10;

    private final PaymentRepository paymentRepository;

    /** 5분마다 확인한다. 10분 기준보다 촘촘해야 방치 시간이 최대 15분을 넘지 않는다 */
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void cleanupStaleReady() {
        LocalDateTime cutoff = LocalDateTime.now(KstClock.ZONE).minusMinutes(STALE_MINUTES);
        int cleaned = paymentRepository.markStaleReadyAsClosed(cutoff);
        if (cleaned > 0) {
            log.info("[결제] 방치 READY {}건 정리 (기준: {}분 경과)", cleaned, STALE_MINUTES);
        }
    }
}
