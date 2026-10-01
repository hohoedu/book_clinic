package com.hohoedu.book_clinic.demo;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hohoedu.book_clinic._core.auth.CustomUserDetails;
import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic.monitor.MonitorSyncService;

import lombok.RequiredArgsConstructor;

/** 시연 데이터 기준점 저장/원복 API (2026-10-01) — 사이드바 "초기화" 버튼(시연 관리자 계정 전용)이 호출한다 */
@RestController
@RequestMapping("/admin/demo")
@RequiredArgsConstructor
public class DemoResetController {

    private final DemoResetService demoResetService;
    private final MonitorSyncService monitorSyncService;

    @PostMapping("/snapshot")
    public ResponseEntity<?> snapshot(@RequestBody Map<String, String> body,
                                      @AuthenticationPrincipal CustomUserDetails userDetails) {
        demoResetService.verify(userDetails, body.get("password"));
        return ResponseEntity.ok(ApiUtils.success(demoResetService.saveSnapshot()));
    }

    @PostMapping("/reset")
    public ResponseEntity<?> reset(@RequestBody Map<String, String> body,
                                   @AuthenticationPrincipal CustomUserDetails userDetails) {
        demoResetService.verify(userDetails, body.get("password"));
        List<Long> newReservationIds = demoResetService.restore();
        // SQL 커밋 뒤에 시연 중 생긴 모니터링 카드도 지운다 — 남겨두면 실시간 모니터링 구독이 유령 카드를 되살린다
        monitorSyncService.deleteCards(newReservationIds);
        return ResponseEntity.ok(ApiUtils.success(null));
    }
}
