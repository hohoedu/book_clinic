package com.hohoedu.book_clinic.schedule.materialize;

import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.hohoedu.book_clinic.schedule._dto.MaterializeDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 예약 오픈 기간을 하루씩 굴려주는 배치.
 *
 * [없으면 무슨 일이 생기나] 슬롯은 규칙을 저장하는 순간 오늘~28일치가 찍힌다. 그 뒤로 아무도
 * 다시 찍지 않으면 날짜가 하루 지날 때마다 예약 가능한 마지막 날이 그대로인 채 남은 일수만
 * 줄어들어, 28일 뒤에는 예약할 수 있는 날이 하나도 없게 된다. 관리자는 스케줄을 멀쩡히
 * 저장해뒀으므로 화면상으로는 아무 이상이 없어 보인다 — 조용히 망가지는 종류의 버그라
 * 이 배치가 반드시 있어야 한다.
 *
 * [멱등하다] materialize는 "이 날짜는 이래야 한다"를 다시 계산해 upsert할 뿐이라 여러 번
 * 돌아도 결과가 같다. 그래서 매일 지평 전체(28일)를 다시 찍는다 — 새로 열리는 하루만
 * 찍으면 빠르지만, 중간에 서버가 꺼져 있던 날이 있으면 그 구멍이 영영 안 메워진다.
 *
 * [예약이 걸린 날짜] 엔진이 알아서 건너뛴다. 이미 예약이 있는 날은 규칙이 어떻든 그대로 둬야
 * 하므로 정상 동작이고, 다만 규칙과 실제 슬롯이 어긋난 상태라는 뜻이라 로그로 남긴다.
 *
 * [기동 시에도 한 번 돈다] 공유 웹호스팅(cafe24 톰캣)이라 새벽 점검·재배포로 03:10에 서버가
 * 내려가 있을 수 있고, 그러면 그날 지평이 안 굴러간다. 기동 시 한 번 더 돌면 재시작 자체가
 * 복구 계기가 되므로, 재시작이 잦은 환경일수록 오히려 안전해진다. 매일 지평 전체를 다시 찍는
 * 멱등한 작업이라 하루에 여러 번 돌아도 결과는 같다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleMaterializeJob {

    private final SlotInstanceRepository slotInstanceRepository;
    private final ScheduleMaterializer scheduleMaterializer;

    /**
     * 매일 새벽 3시 10분(KST). 자정 직후를 피한 이유는 그 시간대에 다른 정산·집계가 몰리기도 하고,
     * KST 자정 = UTC 15시라 날짜 경계 계산이 걸린 작업과 겹치면 원인을 찾기 번거로워서다.
     */
    @Scheduled(cron = "0 10 3 * * *", zone = "Asia/Seoul")
    public void rollReservationWindow() {
        materializeAllCenters("일일 배치");
    }

    /**
     * 기동 직후 따라잡기.
     *
     * 여기서 던지는 예외는 절대 밖으로 나가면 안 된다 — ApplicationReadyEvent 리스너에서 예외가
     * 올라가면 애플리케이션 기동 자체가 실패한다. 슬롯을 못 찍는 것보다 서버가 안 뜨는 쪽이
     * 훨씬 큰 사고라, DB가 잠깐 불안정한 상황에서도 기동은 반드시 성공해야 한다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void materializeOnStartup() {
        try {
            materializeAllCenters("기동");
        } catch (Exception e) {
            log.error("[운영스케줄] 기동 시 슬롯 재생성 실패 — 다음 일일 배치(03:10)에서 자동으로 따라잡는다", e);
        }
    }

    private void materializeAllCenters(String trigger) {
        long startedAt = System.currentTimeMillis();
        List<String> centerCodes = slotInstanceRepository.findCenterCodesWithSchedule();
        if (centerCodes.isEmpty()) {
            return;
        }

        int totalSlots = 0;
        for (String centerCode : centerCodes) {
            try {
                MaterializeDTO.ResultDTO result = scheduleMaterializer.materializeHorizon(centerCode);
                totalSlots += result.getSlotCount();
                if (!result.getBlockedDates().isEmpty()) {
                    log.warn("[운영스케줄] {} — 예약이 있어 건너뛴 날짜: center={}, dates={}",
                            trigger, centerCode, result.getBlockedDates());
                }
            } catch (Exception e) {
                // 한 센터가 실패해도 나머지 센터는 계속 찍어야 한다. 여기서 예외가 올라가면
                // 뒤에 있는 센터들이 통째로 누락되고, 그건 예약 자체가 막히는 사고가 된다.
                log.error("[운영스케줄] {} 실패 — center={}", trigger, centerCode, e);
            }
        }

        log.info("[운영스케줄] {} 완료 — 센터 {}곳, 슬롯 {}건, 오픈 기간 {}일, {}ms",
                trigger, centerCodes.size(), totalSlots, ScheduleMaterializer.RESERVATION_OPEN_DAYS,
                System.currentTimeMillis() - startedAt);
    }

}
