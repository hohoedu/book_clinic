package com.hohoedu.book_clinic.payment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hohoedu.book_clinic._core.utils.ApiUtils;

import lombok.RequiredArgsConstructor;

/**
 * 결제 이상 건(운영자 수동 확인 필요) 관리자 API — 금액 불일치·망취소 실패·승인 확정 실패처럼
 * 코드가 스스로 못 끝내고 사람이 이니시스 상점관리자에서 직접 봐야 하는 결제를 모아 보여준다
 * (2026-08-07). 이 화면이 생기기 전에는 로그에만 남아서 아무도 안 보면 그대로 묻혔다.
 *
 * 처리는 여기서 직접 하지 않는다 — 실제 취소/승인 조치는 이니시스 상점관리자에서 하고,
 * 여기서는 "처리 완료로 표시"만 한다(목록에서 빼는 용도).
 */
@RestController
@RequestMapping("/admin/payment/review")
@RequiredArgsConstructor
public class PaymentReviewController {

    private final PaymentRepository paymentRepository;

    @GetMapping("/list")
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(ApiUtils.success(paymentRepository.findNeedsReview()));
    }

    /**
     * 처리 완료 표시 — WHERE에 needs_review=1 AND reviewed_at IS NULL이 걸려 있어(PaymentMapper.xml),
     * 이미 처리됐거나 확인 대상이 아니었던 paymentId는 그냥 0행 갱신으로 조용히 끝난다(멱등).
     * 관리자가 같은 항목을 두 번 눌러도 에러를 볼 필요는 없다.
     */
    @PostMapping("/{paymentId}/resolve")
    public ResponseEntity<?> resolve(@PathVariable int paymentId) {
        paymentRepository.resolveReview(paymentId);
        return ResponseEntity.ok(ApiUtils.success("처리 완료로 표시했습니다."));
    }
}
