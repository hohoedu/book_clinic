package com.hohoedu.book_clinic._core.view;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.hohoedu.book_clinic.common.code.CodeService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class AdminViewController {

    private final CodeService codeService;

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

    @GetMapping("/admin/book-data")
    public String bookData(Model model) {
        // 분류(대분류)·장르 코드 - 화면 chip/셀렉트/필터 렌더링용
        model.addAttribute("contentTypeCodes", codeService.findBookstoreCodes("C"));
        model.addAttribute("genreCodes", codeService.findBookstoreCodes("G"));
        return "book/book-data";
    }

    @GetMapping("/admin/book-priority")
    public String bookPriority() {
        return "book/book-priority";
    }

    /** 실시간 모니터링 — 카드 그리드는 화면 진입 시 1회 API 호출 + Firestore 구독으로 채워진다 */
    @GetMapping("/admin/monitor/live-view")
    public String monitorLive(Model model) {
        model.addAttribute("firebaseWebApiKey", firebaseWebApiKey);
        model.addAttribute("firebaseWebAuthDomain", firebaseWebAuthDomain);
        model.addAttribute("firebaseWebProjectId", firebaseWebProjectId);
        model.addAttribute("firebaseWebStorageBucket", firebaseWebStorageBucket);
        model.addAttribute("firebaseWebMessagingSenderId", firebaseWebMessagingSenderId);
        model.addAttribute("firebaseWebAppId", firebaseWebAppId);
        return "monitor/monitor-live";
    }

    /** 클리닉 예약 등록 — 학생을 날짜+타임슬롯에 배정하는 화면 (2026-07-23) */
    @GetMapping("/admin/monitor/reservation-view")
    public String reservation() {
        return "monitor/reservation";
    }
}
