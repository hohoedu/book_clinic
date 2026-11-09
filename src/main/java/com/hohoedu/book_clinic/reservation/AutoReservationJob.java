package com.hohoedu.book_clinic.reservation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 매주 자동 예약 배치 (2026-10-27).
 *
 * [03:25] 운영 스케줄이 새 날짜를 여는 ScheduleMaterializeJob(03:10) 뒤에 돈다 — 슬롯이 찍혀
 * 있어야 예약할 수 있다. 이용권이 떨어진 자동구매 학생의 카드 결제도 여기서 나간다.
 *
 * [10:00] 새벽 결과를 학부모가 자고 있을 때 하나씩 보내지 않고, 아침에 학생별로 묶어 한 번 보낸다.
 *
 * [기동 시에는 돌지 않는다] 다른 배치와 달리 카드 결제가 섞여 있어, 재배포할 때마다 결제가 나가는
 * 일을 만들지 않는다. 하루 빠져도 패턴마다 filled_until이 남아 있어 다음 날 배치가 이어서 잡는다.
 *
 * 둘 다 예외를 밖으로 던지지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoReservationJob {

    private final AutoReservationService autoReservationService;

    @Scheduled(cron = "0 25 3 * * *", zone = "Asia/Seoul")
    public void reserve() {
        try {
            autoReservationService.runDaily();
        } catch (Exception e) {
            log.error("[자동예약] 새벽 배치 실패", e);
        }
    }

    @Scheduled(cron = "0 0 10 * * *", zone = "Asia/Seoul")
    public void sendNotices() {
        try {
            autoReservationService.sendNotices();
        } catch (Exception e) {
            log.error("[자동예약] 10시 안내 실패", e);
        }
    }
}
