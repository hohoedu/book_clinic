package com.hohoedu.book_clinic.clinic;

import java.util.ArrayList;
import java.util.List;

/**
 * 스페셜 카드 지급 시점 (2026-10-22 정책).
 *
 * 학년별 목표 권수(1년 권장도서 수)를 {@value #SPECIALS_PER_GRADE}칸으로 나눠, 칸을 채울 때마다 1장씩 준다.
 *   · 간격 = 올림(목표 ÷ 10). 마지막(10번째) 칸은 언제나 목표 권수 그 자체다.
 *     초1·2 = 96권 → 10, 20, … 90, 96 / 초3 = 60권 → 6, 12, … 60 / 초4~6 = 50권 → 5, 10, … 50
 *   · 세는 대상은 "지금 학년에서 받은 일반 카드"다 — 학년이 오르면 0부터 다시 센다.
 *   · 목표 권수를 다 채운 뒤에는 학년이 바뀌기 전까지 더 주지 않는다.
 *
 * 예전에는 학년과 무관하게 평생 누적 10장마다 줬다. 이 판단이 서버·결과 화면·모니터링 세 곳에 숫자 10으로
 * 흩어져 있어서, 규칙을 한 곳에 모으고 나머지는 결과(지급 기록·남은 장수)만 받아 쓰게 했다.
 */
public final class SpecialCardPolicy {

    /** 학년당 스페셜 카드 수 — 학년별 스페셜 카드 그림(special_{학년}_01~10)도 이 수만큼 있다 */
    public static final int SPECIALS_PER_GRADE = 10;

    private SpecialCardPolicy() {
    }

    /** 그 학년에서 스페셜 카드를 주는 누적 장수 목록(오름차순). 목표를 모르면 빈 목록 — 지급하지 않는다 */
    public static List<Integer> thresholds(Integer goal) {
        List<Integer> result = new ArrayList<>();
        if (goal == null || goal <= 0) {
            return result;
        }
        int interval = (goal + SPECIALS_PER_GRADE - 1) / SPECIALS_PER_GRADE;
        for (int k = 1; k < SPECIALS_PER_GRADE && k * interval < goal; k++) {
            result.add(k * interval);
        }
        result.add(goal);
        return result;
    }

    /** 이번 장수가 스페셜 카드를 주는 칸인지 */
    public static boolean isThreshold(Integer goal, int gradeCards) {
        return thresholds(goal).contains(gradeCards);
    }

    /**
     * 그 장수에서 받은 스페셜 카드가 그 학년의 몇 번째인지(1부터) — 그림 번호(special_{학년}_NN)로 쓴다.
     * 정책 변경 전에 "10장마다" 규칙으로 받은 카드는 칸과 딱 맞지 않을 수 있어, 그 장수 이하의 칸 수로
     * 센다(최소 1). 칸 하나 이하에서 받은 예전 카드도 그림이 비지 않게 하기 위해서다.
     */
    public static int ordinal(Integer goal, int triggerCount) {
        int count = 0;
        for (int t : thresholds(goal)) {
            if (t <= triggerCount) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    /** 다음 스페셜 카드까지 남은 장수. 그 학년 목표를 다 채웠으면(또는 목표를 모르면) null */
    public static Integer cardsToNext(Integer goal, int gradeCards) {
        for (int t : thresholds(goal)) {
            if (t > gradeCards) {
                return t - gradeCards;
            }
        }
        return null;
    }
}
