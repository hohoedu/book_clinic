package com.hohoedu.book_clinic._core.view;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

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

    /** 학생 정보 — 화면 스캐폴딩 단계, 하드코딩 목업 데이터로 레이아웃만 구현 (2026-08-24) */
    @GetMapping("/admin/operation/student-view")
    public String studentInfo() {
        return "operation/student-info";
    }
}
