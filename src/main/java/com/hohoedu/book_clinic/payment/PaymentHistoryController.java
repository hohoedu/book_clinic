package com.hohoedu.book_clinic.payment;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hohoedu.book_clinic._core.auth.CenterAccessGuard;
import com.hohoedu.book_clinic._core.auth.CustomUserDetails;
import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.utils.ApiUtils;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 "결제 내역" 화면 조회 API (2026-08-31). 읽기 전용이다.
 *
 * 이미 있는 PaymentReviewController(결제 이상 건)와는 별개 화면이다 — 저쪽은 "코드가 못 끝낸
 * 결제 몇 건"이고, 이쪽은 "이번 달 전원의 납부 현황"이다. 같은 /admin/payment 아래 두되
 * 경로를 /history로 나눈다.
 */
@RestController
@RequestMapping("/admin/payment/history")
@RequiredArgsConstructor
public class PaymentHistoryController {

    /** 화면 "결제 종류" 탭이 보내는 값. 프로그램 이용권(PROGRAM)은 아직 판매 상품이 없어 빈 목록이 나온다 */
    private static final String SERVICE_BOOK = "BOOK";
    private static final String SERVICE_PROGRAM = "PROGRAM";

    private static final DateTimeFormatter BILLING_YM = DateTimeFormatter.ofPattern("yyyyMM");

    private final PaymentAdminService paymentAdminService;
    private final CenterAccessGuard centerAccessGuard;

    /**
     * 목록 + 상단 요약.
     *
     * @param month  화면 month 입력값(YYYY-MM). 비어 있으면 이번 달
     * @param type   결제 종류(BOOK/PROGRAM)
     * @param grade  학년 코드(erp_bookstore_code gubun='S'). 비어 있으면 전체
     * @param status 이용권 상태 뱃지 코드(IN_USE/USED_UP/UNPAID/PARTIAL_REFUND/REFUNDED)
     * @param keyword 학생명 검색어
     */
    @GetMapping("/list")
    public ResponseEntity<?> list(@RequestParam(value = "month", required = false) String month,
                                  @RequestParam(value = "type", required = false) String type,
                                  @RequestParam(value = "grade", required = false) String grade,
                                  @RequestParam(value = "status", required = false) String status,
                                  @RequestParam(value = "keyword", required = false) String keyword,
                                  @AuthenticationPrincipal CustomUserDetails userDetails) {
        String centerCode = centerAccessGuard.requireCenterCode(userDetails);
        return ResponseEntity.ok(ApiUtils.success(paymentAdminService.getHistoryPage(
                centerCode, toBillingYm(month), toServiceCode(type), blankToNull(grade), blankToNull(status),
                blankToNull(keyword))));
    }

    /**
     * 행 펼침 상세 — 차감 내역 + 결제/환불 내역.
     * passId/paymentId는 둘 다 없을 수 있다(미결제 학생). 그 경우 빈 목록 두 개가 나간다.
     */
    @GetMapping("/detail")
    public ResponseEntity<?> detail(@RequestParam("studentId") String studentId,
                                    @RequestParam(value = "passId", required = false) Integer passId,
                                    @RequestParam(value = "paymentId", required = false) Integer paymentId,
                                    @AuthenticationPrincipal CustomUserDetails userDetails) {
        centerAccessGuard.requireStudentInMyCenter(userDetails, studentId);
        return ResponseEntity.ok(ApiUtils.success(
                paymentAdminService.getHistoryDetail(studentId, passId, paymentId)));
    }

    /**
     * "2026-08" → "202608". 형식이 어긋나면 조용히 이번 달로 넘어가지 않고 400으로 튕긴다 —
     * 요청한 달과 다른 달의 숫자가 화면에 뜨는 편이 오류 메시지보다 나쁘다.
     */
    private String toBillingYm(String month) {
        if (month == null || month.isBlank()) {
            return LocalDate.now().format(BILLING_YM);
        }
        if (!month.matches("\\d{4}-\\d{2}")) {
            throw new Exception400("이용 월 형식이 올바르지 않습니다: " + month);
        }
        return month.replace("-", "");
    }

    private String toServiceCode(String type) {
        if (type == null || type.isBlank() || SERVICE_BOOK.equals(type)) {
            return SERVICE_BOOK;
        }
        if (SERVICE_PROGRAM.equals(type)) {
            return SERVICE_PROGRAM;
        }
        throw new Exception400("알 수 없는 결제 종류입니다: " + type);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
