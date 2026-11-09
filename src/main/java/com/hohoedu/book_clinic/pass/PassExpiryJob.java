package com.hohoedu.book_clinic.pass;

import java.time.LocalDate;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.hohoedu.book_clinic._core.utils.KstClock;
import com.hohoedu.book_clinic.pass._dto.PassRespDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 이용권 기간 배치 (2026-10-27) — 유효기간 자동 시작 + 서비스 제공기간 종료 시 쿠폰 전환.
 *
 * ① 결제 후 30일 동안 첫 예약이 없으면 결제일 + 30일부터 유효기간을 시작시킨다.
 * ② 서비스 제공기간(결제일 + 90일)이 끝난 이용권은 남은 횟수를 소멸시키고 같은 수만큼 쿠폰을 준다.
 * ①을 먼저 돌린다 — 쿠폰 기간이 유효기간 끝에서 정해지므로, 한 번도 예약하지 않은 이용권도
 * 기간이 잡힌 뒤에 전환돼야 쿠폰이 나온다.
 *
 * [배치가 늦어도 괜찮은 이유] 서비스 기간이 끝난 이용권은 조회/차감 쿼리가 이미 쓸 수 없는 것으로
 * 본다(PassMapper inServicePeriod). 이 배치는 남은 횟수를 쿠폰으로 옮겨주는 일만 하므로, 몇 시간
 * 늦게 돌아도 그 사이 잘못 쓰이는 횟수는 없다.
 *
 * [ReservationNoShowJob과 같은 패턴] 매일 새벽 실행 + 기동 시 한 번 더 + 예외를 밖으로 던지지 않음.
 * 둘 다 멱등하다 — 자동 시작은 미배정만, 전환은 converted_at IS NULL만 건드린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PassExpiryJob {

    private final PassService passService;

    /** 매일 00:05(KST) — 날짜가 바뀌자마자 전날로 끝난 서비스 기간을 정리한다 */
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
    public void runDaily() {
        run("일일 배치");
    }

    /** 기동 직후 따라잡기 — 재배포로 새벽 배치가 빠지는 것 대비 */
    @EventListener(ApplicationReadyEvent.class)
    public void runOnStartup() {
        try {
            run("기동");
        } catch (Exception e) {
            log.error("[이용권] 기동 시 기간 배치 실패 — 다음 일일 배치(00:05)에서 따라잡는다", e);
        }
    }

    private void run(String trigger) {
        LocalDate today = KstClock.today();
        int started = passService.autoStartUnassigned(today);

        int converted = 0;
        int coupons = 0;
        for (PassRespDTO.PassDTO pass : passService.findServiceEnded(today)) {
            try {
                coupons += passService.convertToCoupon(pass.getPassId());
                converted++;
            } catch (Exception e) {
                // 한 장이 실패해도 나머지는 전환돼야 한다. 실패한 장은 converted_at이 그대로라 다음 배치가 다시 집는다.
                log.error("[이용권] 쿠폰 전환 실패 — passId={}", pass.getPassId(), e);
            }
        }
        log.info("[이용권] 기간 배치 {} 완료 — 자동 시작 {}장, 서비스 종료 {}장, 쿠폰 {}장",
                trigger, started, converted, coupons);
    }
}
