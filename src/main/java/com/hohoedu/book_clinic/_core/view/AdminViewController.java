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

    @GetMapping({"/", "/admin/book-data"})
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

    /** 운영 스케줄 설정 — 정적 스캐폴딩 단계, 저장/조회 API 연동은 다음 작업에서 이어감 (2026-08-14) */
    @GetMapping("/admin/operation/schedule")
    public String operationSchedule() {
        return "operation/operation-schedule";
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

    /** 예약 현황 — 회차별 예약 현황 조회 및 예약 변경/취소 화면 (2026-08-19) */
    @GetMapping("/admin/monitor/reservation-view")
    public String reservation() {
        return "reservation/reservation";
    }

    /** 독서일지 — 정적 스캐폴딩 단계, 조회/저장 API 연동은 다음 작업에서 이어감 (2026-07-29) */
    @GetMapping("/admin/growth/diary")
    public String diary() {
        return "growth/diary";
    }

    /**
     * 결제 이상 건 — 금액 불일치·망취소 실패·승인 확정 실패처럼 코드가 스스로 못 끝내고
     * 사람이 이니시스 상점관리자에서 직접 확인해야 하는 결제 목록 (2026-08-07)
     */
    @GetMapping("/admin/payment/review-view")
    public String paymentReview() {
        return "payment/payment-review";
    }
}
