package com.hohoedu.book_clinic.reservation;

import java.time.LocalDateTime;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.hohoedu.book_clinic._core.utils.KstClock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 노쇼 배치 — 슬롯 종료 시각이 지났는데 아직 RESERVED로 남은 예약을 NOSHOW로 전환한다 (2026-08-18).
 *
 * [ATTENDED는 왜 여기 없나] 출석 전환은 입실 시점에 바로 일어나야 정확해서
 * {@code MonitorService.enterSession}에서 직접 처리한다. 이 배치는 그 반대 — "안 왔다"는
 * 어느 시점이 지나야만 확정할 수 있으므로 배치로 돈다.
 *
 * [멱등하다] transitionStatus가 RESERVED일 때만 NOSHOW로 바꾸므로, 하루에 여러 번 돌아도
 * 이미 전환된 건은 다시 안 건드린다.
 *
 * [ScheduleMaterializeJob과 같은 패턴] 매일 새벽 실행 + 기동 시 한 번 더(재배포로 새벽 배치가
 * 통째로 빠지는 걸 대비) + 예외를 절대 밖으로 던지지 않음(기동 실패 방지)을 그대로 따른다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationNoShowJob {

    private final ReservationService reservationService;

    /**
     * 매일 새벽 3시(KST) — ScheduleMaterializeJob(03:10)보다 앞서 돈다. 그날 하루가 완전히
     * 지난 뒤(자정을 넘겨서까지 운영하는 센터는 없다고 가정) 확정하기 위해 새벽 시간대를 쓴다.
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void markNoShows() {
        run("일일 배치");
    }

    /** 기동 직후 따라잡기 — ScheduleMaterializeJob과 동일한 이유(공유호스팅 재배포로 새벽 배치 누락 대비) */
    @EventListener(ApplicationReadyEvent.class)
    public void markNoShowsOnStartup() {
        try {
            run("기동");
        } catch (Exception e) {
            log.error("[예약] 기동 시 노쇼 처리 실패 — 다음 일일 배치(03:00)에서 자동으로 따라잡는다", e);
        }
    }

    private void run(String trigger) {
        long startedAt = System.currentTimeMillis();
        LocalDateTime asOf = LocalDateTime.now(KstClock.ZONE);
        int count = reservationService.markNoShows(asOf);
        log.info("[예약] 노쇼 처리 {} 완료 — {}건, {}ms", trigger, count, System.currentTimeMillis() - startedAt);
    }

}
