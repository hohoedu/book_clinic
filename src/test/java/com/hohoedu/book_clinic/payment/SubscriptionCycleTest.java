package com.hohoedu.book_clinic.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.hohoedu.book_clinic.pass.PassService;

/**
 * 자동결제 주기 계산 — 2026-09-07 전환.
 *
 * 앵커일(매월 청구 일자) 계산은 말일이 걸린 달에서 틀리기 쉽고, 한 번 틀리면 매달 조금씩
 * 앞당겨지며 누적된다. 그래서 31일 앵커가 2월을 지나 3월에 제자리로 돌아오는지를 못박아 둔다.
 */
class SubscriptionCycleTest {

    @Test
    @DisplayName("주기 종료일은 다음 청구일의 전날이다")
    void cycleEnd_한달_뒤_전일() {
        assertEquals(LocalDate.of(2026, 10, 14), PassService.cycleEnd(LocalDate.of(2026, 9, 15)));
    }

    @Test
    @DisplayName("말일이 짧은 달로 넘어가도 주기가 겹치거나 비지 않는다")
    void cycleEnd_말일_경계() {
        // 1/31 결제 → 2/27까지(2/28이 다음 청구일이므로)
        assertEquals(LocalDate.of(2026, 2, 27), PassService.cycleEnd(LocalDate.of(2026, 1, 31)));
    }

    @Test
    @DisplayName("앵커일이 그 달에 없으면 말일로 당긴다")
    void nextAnchorDate_없는_날짜는_말일() {
        assertEquals(LocalDate.of(2026, 2, 28),
                SubscriptionService.nextAnchorDate(LocalDate.of(2026, 1, 31), 31));
    }

    @Test
    @DisplayName("말일로 당겨졌던 앵커는 다음 달에 원래 날짜로 돌아온다")
    void nextAnchorDate_앵커는_보존된다() {
        // 2월에 28일로 밀렸지만 앵커(31)는 구독에 그대로 남아 있으므로 3월엔 31일이다.
        // 앵커를 보존하지 않고 직전 청구일만 따라가면 매달 조금씩 앞당겨져 결국 28일로 굳는다.
        assertEquals(LocalDate.of(2026, 3, 31),
                SubscriptionService.nextAnchorDate(LocalDate.of(2026, 2, 28), 31));
    }

    @Test
    @DisplayName("주문번호는 주기로부터 결정적으로 만들어진다 — 같은 주기 재청구를 PG가 거절하게 하려고")
    void groupOrderNo_결정적() {
        String first = SubscriptionTxService.groupOrderNo(12, LocalDate.of(2026, 9, 7));
        String again = SubscriptionTxService.groupOrderNo(12, LocalDate.of(2026, 9, 7));
        assertEquals(first, again);
        assertEquals("SUB12-20260907", first);
    }
}
