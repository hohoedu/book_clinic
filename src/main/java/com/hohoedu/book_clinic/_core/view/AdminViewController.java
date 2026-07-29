package com.hohoedu.book_clinic._core.view;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.hohoedu.book_clinic._core.auth.CustomUserDetails;
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

    /** 보유도서 설정 — 로그인 직원 센터의 도서별 보유 수량을 조회/조정하는 화면 */
    @GetMapping("/admin/book-stock")
    public String bookStock(Model model) {
        // 학년·분류·카테고리 필터 셀렉트 렌더링용
        model.addAttribute("schoolYearCodes", codeService.findBookstoreCodes("S"));
        model.addAttribute("contentTypeCodes", codeService.findBookstoreCodes("C"));
        model.addAttribute("genreCodes", codeService.findBookstoreCodes("G"));
        return "book/book-stock";
    }

    @GetMapping("/admin/book-priority")
    public String bookPriority() {
        return "book/book-priority";
    }

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

    /** 클리닉 예약 등록 — 학생을 날짜+타임슬롯에 배정하는 화면 (2026-07-23) */
    @GetMapping("/admin/monitor/reservation-view")
    public String reservation() {
        return "monitor/reservation";
    }

    /** 독서일지 — 정적 스캐폴딩 단계, 조회/저장 API 연동은 다음 작업에서 이어감 (2026-07-29) */
    @GetMapping("/admin/growth/diary")
    public String diary() {
        return "growth/diary";
    }
}
