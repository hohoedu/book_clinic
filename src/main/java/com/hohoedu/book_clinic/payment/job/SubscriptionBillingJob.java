package com.hohoedu.book_clinic.payment.job;

import java.time.LocalDate;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.hohoedu.book_clinic._core.utils.KstClock;
import com.hohoedu.book_clinic.payment.SubscriptionService;
import com.hohoedu.book_clinic.payment._dto.SubscriptionRespDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 매월 자동결제 청구 배치 — 2026-09-07.
 *
 * [왜 새벽인가] 청구는 학부모가 자고 있는 사이에 끝나 있어야 한다. 아침에 앱을 열었을 때
 * 이미 이번 달 이용권이 충전돼 있어야 예약을 잡을 수 있기 때문이다. 카드사 점검 시간대
 * (보통 23:30~00:30)를 피해 03:20으로 둔다.
 *
 * [하루 한 번으로 충분한 이유] 앵커일은 날짜 단위라 시각이 중요하지 않다. 실패한 건은
 * 재시도 일정(D+1, D+3, D+5)이 다음 날 배치로 자연히 이어진다.
 *
 * [이중 청구를 어떻게 막나] 이 배치가 두 번 뜨거나 인스턴스가 둘이어도 같은 주기가 두 번
 * 나가면 안 된다. 방어는 SubscriptionService.chargeDue가 한다 —
 *   ① next_billing_on 조건부 UPDATE로 선점(먼저 집은 쪽만 진행)
 *   ② 주기로부터 결정적으로 만든 주문번호(같은 주기 재청구는 PG가 중복 oid로 거절)
 *   ③ payment의 (student_id, service_code, cycle_from) 유니크 인덱스
 * 그래서 이 클래스는 목록을 훑고 예외를 삼키는 것 외에 판단을 하지 않는다.
 *
 * [한 건이 실패해도 멈추지 않는다] 구독 하나에서 예외가 나도 나머지는 청구돼야 한다.
 * 그날 청구를 통째로 건너뛰면 전 학생의 이용권이 하루 늦게 나간다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionBillingJob {

    /** 카드등록창까지 갔다가 이탈한 PENDING 구독을 닫는 기준(시간) */
    private static final int STALE_PENDING_HOURS = 24;

    private final SubscriptionService subscriptionService;

    @Scheduled(cron = "0 20 3 * * *", zone = "Asia/Seoul")
    public void chargeDueSubscriptions() {
        LocalDate today = KstClock.today();
        List<SubscriptionRespDTO.SubscriptionDTO> due = subscriptionService.findDue(today);
        if (due.isEmpty()) {
            return;
        }

        int tried = 0;
        int skipped = 0;
        for (SubscriptionRespDTO.SubscriptionDTO sub : due) {
            try {
                if (subscriptionService.chargeDue(sub)) {
                    tried++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                // 여기까지 올라온 예외는 청구 로직이 스스로 처리하지 못한 것이다. 다음 구독을
                // 계속 처리하되, 이 건은 선점으로 다음 시도일이 이미 밀려 있으므로 그대로 두면
                // 다음 앵커일에 다시 시도된다(그 사이 이용권이 비는 것은 사람이 봐야 한다).
                log.error("[자동결제] 청구 중 예외 — subscriptionId={}", sub.getSubscriptionId(), e);
            }
        }
        log.info("[자동결제] 배치 완료 — 대상 {}건, 시도 {}건, 건너뜀 {}건", due.size(), tried, skipped);
    }

    /**
     * 카드등록창에서 이탈한 PENDING 구독 정리 — 결제의 READY 정리와 같은 성격이다.
     * 빌키가 없어 돈과는 무관하고, 남겨두면 그 학생이 재등록할 때 목록만 지저분해진다.
     */
    @Scheduled(cron = "0 40 3 * * *", zone = "Asia/Seoul")
    public void closeStaleCardReg() {
        int closed = subscriptionService.closeStalePending(KstClock.now().minusHours(STALE_PENDING_HOURS));
        if (closed > 0) {
            log.info("[자동결제] 미완료 카드등록 정리 — {}건", closed);
        }
    }
}
