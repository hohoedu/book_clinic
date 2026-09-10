package com.hohoedu.book_clinic.payment;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic._core.utils.KstClock;
import com.hohoedu.book_clinic.payment._dto.SubscriptionRespDTO;
import com.hohoedu.book_clinic.payment.job.SubscriptionBillingJob;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 자동결제 배치 수동 트리거 (dev 프로파일 전용) — 2026-09-09.
 *
 * [왜 필요한가] 재청구 배치는 매일 03:20에만 돈다. 첫 결제일(first_billing_on)을 미래로
 * 잡은 구독이 실제로 그날 청구되는지, 앵커 주기가 제대로 밀리는지 확인하려면 스케줄을
 * 기다리지 않고 그 자리에서 배치를 돌릴 수단이 필요하다.
 *
 * 배치와 완전히 같은 코드(SubscriptionBillingJob / SubscriptionService.chargeDue)를 부른다.
 * 테스트에서만 통하는 별도 경로를 만들면 "테스트는 되는데 실제로는 안 되는" 상황이 생긴다.
 *
 * [@Profile("dev")] 운영에는 이 진입점이 뜨지 않는다. 인증 없이 남의 카드로 청구를
 * 일으킬 수 있는 주소가 운영에 열려 있으면 안 되기 때문이다.
 */
@Slf4j
@RestController
@Profile("dev")
@RequestMapping("/payment/subscription/test")
@RequiredArgsConstructor
public class SubscriptionTestController {

    private final SubscriptionBillingJob billingJob;
    private final SubscriptionService subscriptionService;

    /** 오늘 청구 대상(next_billing_on <= 오늘, ACTIVE) 목록만 조회 — 배치를 돌리지 않는다 */
    @GetMapping("/due")
    public ResponseEntity<?> due() {
        LocalDate today = KstClock.today();
        List<SubscriptionRespDTO.SubscriptionDTO> due = subscriptionService.findDue(today);
        return ResponseEntity.ok(ApiUtils.success(Map.of("today", today, "count", due.size(), "due", due)));
    }

    /**
     * 재청구 배치를 지금 1회 실행 — 스케줄(03:20)과 같은 코드다.
     * next_billing_on 이 오늘 이하인 ACTIVE 구독을 훑어 청구한다. 결과는 서버 로그에서 본다.
     */
    @PostMapping("/run-batch")
    public ResponseEntity<?> runBatch() {
        LocalDate today = KstClock.today();
        int before = subscriptionService.findDue(today).size();
        log.info("[자동결제][수동] 재청구 배치 실행 요청 — 대상 {}건", before);
        billingJob.chargeDueSubscriptions();
        int after = subscriptionService.findDue(today).size();
        return ResponseEntity.ok(ApiUtils.success(Map.of(
                "today", today, "dueBefore", before, "dueAfter", after,
                "message", "배치 실행 완료 — 상세는 서버 로그 확인")));
    }

    /**
     * 앵커일을 기다리지 않고 특정 구독을 지금 1건 청구 — first_billing_on 이 미래여도 강제로 낸다.
     * 예: POST /payment/subscription/test/charge?subscriptionId=11
     */
    @PostMapping("/charge")
    public ResponseEntity<?> charge(@RequestParam("subscriptionId") int subscriptionId) {
        boolean tried = subscriptionService.chargeNow(subscriptionId);
        return ResponseEntity.ok(ApiUtils.success(Map.of(
                "subscriptionId", subscriptionId, "tried", tried,
                "message", tried ? "청구 시도 — 결과는 서버 로그 확인" : "선점 실패(이미 이번 주기가 처리됨)")));
    }

    /** 카드등록창에서 이탈해 PENDING으로 남은 구독을 지금 정리 — 스케줄(03:40)과 같은 코드다 */
    @PostMapping("/close-stale")
    public ResponseEntity<?> closeStale(@RequestParam(name = "hours", defaultValue = "24") int hours) {
        int closed = subscriptionService.closeStalePending(KstClock.now().minusHours(hours));
        return ResponseEntity.ok(ApiUtils.success(Map.of("closed", closed)));
    }
}
