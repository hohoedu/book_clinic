package com.hohoedu.book_clinic._core.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * SQL 쪽 DATEADD(HOUR, 9, GETUTCDATE()) 컨벤션과 맞추기 위한 Java 쪽 KST 기준점.
 * 서버 OS/컨테이너의 JVM 기본 타임존이 UTC인 환경에 배포되면 LocalDate.now()가
 * KST 자정~오전 9시 사이에 하루 전 날짜를 반환해 세션 조회/생성이 어긋나므로,
 * "오늘 날짜"가 필요한 곳은 반드시 이 클래스를 거친다.
 */
public final class KstClock {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private KstClock() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /** DB의 DATEADD(HOUR, 9, GETUTCDATE())와 같은 벽시계 값 — 시각 비교(입실 허용 시간대 등)에 쓴다 */
    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
