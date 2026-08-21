package com.hohoedu.book_clinic._core.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

public final class KstClock {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private KstClock() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
