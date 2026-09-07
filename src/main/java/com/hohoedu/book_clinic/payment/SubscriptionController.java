package com.hohoedu.book_clinic.payment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hohoedu.book_clinic._core.handler.exception.Exception401;
import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic.payment._dto.SubscriptionReqDTO;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 자동결제 API — 상태 조회 / 해지 / 실패 재시도 (2026-09-07).
 *
 * [카드등록이 여기 없는 이유] 등록은 결제창을 띄우는 화면 흐름이라 BillingRegViewController가
 * 담당한다. 이 컨트롤러는 등록이 끝난 뒤의 관리 동작만 맡는다.
 *
 * [인증] PaymentController와 같은 방식이다. body/쿼리의 studentId를 그대로 믿으면 남의
 * 자동결제를 해지시킬 수 있으므로 세션의 studentId와 반드시 대조한다. "그 학생의 구독이
 * 맞는가"는 그 뒤에 SubscriptionService가 한 번 더 본다.
 */
@Slf4j
@RestController
@RequestMapping("/payment/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private static final String SESSION_STUDENT_ID = "studentId";

    private final SubscriptionService subscriptionService;

    private void requireOwnStudent(HttpServletRequest request, String requestedStudentId) {
        HttpSession session = request.getSession(false);
        Object sessionStudentId = session == null ? null : session.getAttribute(SESSION_STUDENT_ID);
        if (sessionStudentId == null || !sessionStudentId.equals(requestedStudentId)) {
            throw new Exception401("로그인이 필요합니다.");
        }
    }

    /** 자동결제 상태 — 등록 전이면 status=NONE으로 내려간다(화면이 등록 유도를 띄운다) */
    @GetMapping("/status")
    public ResponseEntity<?> status(@RequestParam("studentId") String studentId, HttpServletRequest request) {
        requireOwnStudent(request, studentId);
        return ResponseEntity.ok(ApiUtils.success(subscriptionService.status(studentId)));
    }

    /**
     * 해지 — 다음 주기부터 청구하지 않는다.
     * 이미 결제된 주기의 이용권은 그대로 남는다(돈을 받은 기간이라 회수할 근거가 없다).
     * 중도 환불이 필요하면 기존 환불 API(/payment/refund)가 규정대로 처리한다.
     */
    @PostMapping("/cancel")
    public ResponseEntity<?> cancel(@Valid @RequestBody SubscriptionReqDTO.CancelDTO reqDTO,
                                    HttpServletRequest request) {
        requireOwnStudent(request, reqDTO.getStudentId());
        subscriptionService.cancel(reqDTO.getStudentId(), reqDTO.getSubscriptionId());
        return ResponseEntity.ok(ApiUtils.success(null));
    }

    /**
     * 중지된 자동결제 재시도 — 카드 문제를 해결한 학부모가 직접 다시 돌린다.
     * 카드가 계속 막힌 상태에서 배치가 매일 긁으면 카드사 쪽에 이상거래로 잡힐 수 있어
     * 자동 재개는 하지 않는다.
     */
    @PostMapping("/retry")
    public ResponseEntity<?> retry(@Valid @RequestBody SubscriptionReqDTO.RetryDTO reqDTO,
                                   HttpServletRequest request) {
        requireOwnStudent(request, reqDTO.getStudentId());
        boolean charged = subscriptionService.retry(reqDTO.getStudentId(), reqDTO.getSubscriptionId());
        return ResponseEntity.ok(ApiUtils.success(charged));
    }
}
