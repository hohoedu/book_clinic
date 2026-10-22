package com.hohoedu.book_clinic.clinic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 스페셜 카드 학년별 칸(2026-10-22 정책) — 학년별 목표 권수를 10칸으로 나누고, 마지막 칸은 목표 권수.
 */
class SpecialCardPolicyTest {

    @Test
    void 초1_2학년_96권은_10권마다_주고_10번째는_96권째() {
        assertEquals(List.of(10, 20, 30, 40, 50, 60, 70, 80, 90, 96), SpecialCardPolicy.thresholds(96));
    }

    @Test
    void 초3학년_60권은_6권마다() {
        assertEquals(List.of(6, 12, 18, 24, 30, 36, 42, 48, 54, 60), SpecialCardPolicy.thresholds(60));
    }

    @Test
    void 초4_5학년_50권은_5권마다() {
        assertEquals(List.of(5, 10, 15, 20, 25, 30, 35, 40, 45, 50), SpecialCardPolicy.thresholds(50));
    }

    @Test
    void 목표를_다_채우면_더_주지_않는다() {
        assertTrue(SpecialCardPolicy.isThreshold(60, 60));
        assertFalse(SpecialCardPolicy.isThreshold(60, 66));
        assertNull(SpecialCardPolicy.cardsToNext(60, 60));
        assertNull(SpecialCardPolicy.cardsToNext(96, 97));
    }

    @Test
    void 다음_스페셜까지_남은_장수() {
        assertEquals(6, SpecialCardPolicy.cardsToNext(60, 0));    // 학년이 바뀌어 0부터 → 6권 읽으면 1장
        assertEquals(1, SpecialCardPolicy.cardsToNext(60, 5));
        assertEquals(6, SpecialCardPolicy.cardsToNext(96, 90));   // 90 다음 칸은 96
    }

    @Test
    void 그림_번호는_그_학년의_몇_번째_스페셜인지() {
        assertEquals(1, SpecialCardPolicy.ordinal(60, 6));
        assertEquals(10, SpecialCardPolicy.ordinal(96, 96));
        // 정책 변경 전 "10장마다"로 받은 카드(3학년 10장째)는 칸과 안 맞아도 그림이 비지 않는다
        assertEquals(1, SpecialCardPolicy.ordinal(60, 10));
    }

    @Test
    void 목표를_모르는_학년은_주지_않는다() {
        assertTrue(SpecialCardPolicy.thresholds(null).isEmpty());
        assertNull(SpecialCardPolicy.cardsToNext(null, 3));
    }
}
