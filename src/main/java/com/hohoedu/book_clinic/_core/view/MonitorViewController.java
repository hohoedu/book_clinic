package com.hohoedu.book_clinic._core.view;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.hohoedu.book_clinic._core.auth.CustomUserDetails;

@Controller
public class MonitorViewController {

    @Value("${FIREBASE_WEB_API_KEY}")
    private String firebaseWebApiKey;
    @Value("${FIREBASE_WEB_AUTH_DOMAIN}")
    private String firebaseWebAuthDomain;
    @Value("${FIREBASE_WEB_PROJECT_ID}")
    private String firebaseWebProjectId;
    @Value("${FIREBASE_WEB_STORAGE_BUCKET}")
    private String firebaseWebStorageBucket;
    @Value("${FIREBASE_WEB_MESSAGING_SENDER_ID}")
    private String firebaseWebMessagingSenderId;
    @Value("${FIREBASE_WEB_APP_ID}")
    private String firebaseWebAppId;

    /** 실시간 모니터링 — 카드 그리드는 화면 진입 시 1회 API 호출 + Firestore 구독으로 채워진다 */
    @GetMapping("/admin/monitor/live-view")
    public String monitorLive(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        // 모니터링은 로그인 직원의 센터로만 스코핑한다 — 브라우저 Firestore 구독도 이 센터로 필터
        model.addAttribute("centerCode", userDetails.getLoginUser().getCenterCode());
        model.addAttribute("firebaseWebApiKey", firebaseWebApiKey);
        model.addAttribute("firebaseWebAuthDomain", firebaseWebAuthDomain);
        model.addAttribute("firebaseWebProjectId", firebaseWebProjectId);
        model.addAttribute("firebaseWebStorageBucket", firebaseWebStorageBucket);
        model.addAttribute("firebaseWebMessagingSenderId", firebaseWebMessagingSenderId);
        model.addAttribute("firebaseWebAppId", firebaseWebAppId);
        return "monitor/monitor-live";
    }

    /** 예약 현황 — 회차별 예약 현황 조회 및 예약 변경/취소 화면 (2026-08-19) */
    @GetMapping("/admin/monitor/reservation-view")
    public String reservation() {
        return "monitor/reservation";
    }
}
