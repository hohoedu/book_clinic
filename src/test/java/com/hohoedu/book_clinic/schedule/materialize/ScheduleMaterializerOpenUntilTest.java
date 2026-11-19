package com.hohoedu.book_clinic.schedule.materialize;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** 서당 병행 센터 운영스케줄 달 단위 오픈(2026-11-19) — 전월 19일에 다음 달 말일까지 열린다 */
class ScheduleMaterializerOpenUntilTest {

    @Test
    void 십팔일까지는_이번달_말일까지() {
        assertEquals(LocalDate.of(2026, 11, 30), ScheduleMaterializer.monthlyOpenUntil(LocalDate.of(2026, 11, 1)));
        assertEquals(LocalDate.of(2026, 11, 30), ScheduleMaterializer.monthlyOpenUntil(LocalDate.of(2026, 11, 18)));
    }

    @Test
    void 십구일부터는_다음달_말일까지() {
        assertEquals(LocalDate.of(2026, 12, 31), ScheduleMaterializer.monthlyOpenUntil(LocalDate.of(2026, 11, 19)));
        assertEquals(LocalDate.of(2027, 1, 31), ScheduleMaterializer.monthlyOpenUntil(LocalDate.of(2026, 12, 19)));
        assertEquals(LocalDate.of(2027, 2, 28), ScheduleMaterializer.monthlyOpenUntil(LocalDate.of(2027, 1, 31)));
    }
}
