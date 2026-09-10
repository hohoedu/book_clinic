package com.hohoedu.book_clinic._core.view;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.hohoedu.book_clinic._core.auth.CustomUserDetails;
import com.hohoedu.book_clinic.common.code.CodeService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class OperationViewController {

    private final CodeService codeService;

    /** 보유도서 설정 — 로그인 직원 센터의 도서별 보유 수량을 조회/조정하는 화면 */
    @GetMapping("/admin/book-stock")
    public String bookStock(Model model) {
        // 학년·분류·카테고리 필터 셀렉트 렌더링용
        model.addAttribute("schoolYearCodes", codeService.findBookstoreCodes("S"));
        model.addAttribute("contentTypeCodes", codeService.findBookstoreCodes("C"));
        model.addAttribute("genreCodes", codeService.findBookstoreCodes("G"));
        return "operation/book-stock";
    }

    /** 운영 스케줄 설정 — 정적 스캐폴딩 단계, 저장/조회 API 연동은 다음 작업에서 이어감 (2026-08-14) */
    @GetMapping("/admin/operation/schedule")
    public String operationSchedule() {
        return "operation/operation-schedule";
    }

    /**
     * 결제 이상 건 — 금액 불일치·망취소 실패·승인 확정 실패처럼 코드가 스스로 못 끝내고
     * 사람이 이니시스 상점관리자에서 직접 확인해야 하는 결제 목록 (2026-08-07)
     */
    @GetMapping("/admin/payment/review-view")
    public String paymentReview() {
        return "operation/payment-review";
    }

    /**
     * 결제 내역 — 이용월 기준으로 센터 재원생 전원의 납부/이용권 현황을 보여준다 (2026-08-31).
     * "결제 이상 건"(위)과 헷갈리기 쉬운데 저쪽은 운영자가 손대야 하는 예외 건만 모은 화면이다.
     */
    @GetMapping("/admin/payment/history-view")
    public String paymentHistory(Model model) {
        // 학년 필터 셀렉트 렌더링용 — 목록 API와 별개로 화면 진입 시 한 번만 필요해서 모델로 내린다
        model.addAttribute("schoolYearCodes", codeService.findBookstoreCodes("S"));
        return "operation/payment-history";
    }

    /** 학생 정보 — 목록/필터/상세는 /admin/students/* API로 실데이터를 쓴다 (2026-08-26) */
    @GetMapping("/admin/operation/student-view")
    public String studentInfo(Model model, @AuthenticationPrincipal CustomUserDetails userDetails) {
        // 가입 링크복사 버튼이 붙일 centerCode 쿼리스트링용 — 로그인 직원의 센터로 스코핑
        model.addAttribute("centerCode", userDetails.getLoginUser().getCenterCode());
        return "operation/student-info";
    }
}
