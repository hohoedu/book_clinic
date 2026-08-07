package com.hohoedu.book_clinic.payment;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hohoedu.book_clinic.pass.PassService;
import com.hohoedu.book_clinic.payment._dto.PaymentRespDTO;

import lombok.RequiredArgsConstructor;

/**
 * 결제 확정/환불 반영의 DB 작업만 모아둔 트랜잭션 경계.
 *
 * [왜 PaymentService에서 떼어냈나] 승인은 이니시스 HTTP 호출(최대 15초)과 DB 갱신이
 * 이어지는 흐름인데, 이걸 한 메서드에 @Transactional로 묶으면 커넥션을 쥔 채로 외부 응답을
 * 기다리게 된다. 결제가 몰리는 시간대에 커넥션 풀이 마르는 전형적인 원인이다.
 * 그렇다고 같은 클래스 안에서 @Transactional 메서드를 호출하면 프록시를 타지 않아
 * 트랜잭션이 아예 걸리지 않으므로, 별도 빈으로 분리해야 한다.
 *
 * 여기 메서드들은 "PG 응답을 받은 뒤 우리 DB를 어떻게 바꿀지"만 책임진다.
 * PG를 부를지 말지, 응답을 믿을지는 PaymentService가 정한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentTxService {

    private final PaymentRepository paymentRepository;
    private final PassService passService;

    /**
     * 승인 확정 — 결제를 PAID로 올리고 같은 트랜잭션에서 이용권을 발급한다.
     *
     * 둘이 갈라지면 "돈은 받았는데 횟수가 없다"(고객 항의) 또는 "횟수는 줬는데 결제가 없다"(손실)가
     * 되므로 반드시 한 트랜잭션이어야 한다. 여기서 예외가 나면 호출부가 망취소로 넘어간다.
     *
     * @return 이미 PAID여서 갱신할 것이 없었으면 false (앱의 승인 재시도)
     */
    @Transactional
    public boolean confirmPaid(PaymentRespDTO.PaymentDTO payment, PaymentRespDTO.ProductDTO product,
                               String tid, String payMethod, String cardName, String cardNo,
                               String applNo, String resultCode) {

        // tid 자체엔 더 이상 DB UNIQUE 제약이 없다(형제 묶음결제는 같은 tid를 여러 행에 정당하게
        // 나눠 쓴다). 그 밖의 경우 — 같은 tid가 다른 주문/다른 그룹에 이미 쓰였다면 — 은 여전히
        // 이상 징후이므로 여기서 직접 확인해 막는다. 조용히 기록하는 대신 예외로 세워 사람이 보게 한다.
        int elsewhere = paymentRepository.countByTidElsewhere(tid, payment.getOrderNo(), payment.getGroupOrderNo());
        if (elsewhere > 0) {
            throw new IllegalStateException(
                    "tid가 다른 주문에 이미 사용되었습니다 — orderNo=" + payment.getOrderNo() + ", tid=" + tid);
        }

        int updated = paymentRepository.markPaid(payment.getOrderNo(), tid, payMethod,
                cardName, cardNo, applNo, resultCode);
        if (updated == 0) {
            return false;
        }

        passService.grant(payment.getStudentId(), payment.getCenterCode(), product.getProductId(),
                product.getServiceCode(), PassService.SOURCE_PG, payment.getOrderNo(),
                payment.getBillingYm(), product.getTotalCount());
        return true;
    }

    /** 승인 실패 확정 — 이니시스가 실제로 응답을 준 경우에만 쓴다 */
    @Transactional
    public void confirmFailed(String orderNo, String resultCode) {
        paymentRepository.markFailed(orderNo, resultCode);
    }

    /**
     * 형제 묶음결제 승인 확정 — confirmPaid를 학생 수만큼 반복한다.
     * 한 트랜잭션 안에서 돌아야 "형제 중 일부만 결제 확정" 상태가 생기지 않는다.
     *
     * @return 그룹원 전원이 새로 확정됐으면 true. 한 명이라도 이미 PAID라서 갱신할 게 없었으면
     *         false — 동시 승인 경합(같은 그룹에 서로 다른 tid로 이중 승인)일 수 있다는 신호라
     *         호출부(PaymentService)가 이 값을 보고 추가 확인을 한다(2026-08-07).
     */
    @Transactional
    public boolean confirmPaidGroup(List<PaymentRespDTO.PaymentDTO> payments, PaymentRespDTO.ProductDTO product,
                                    String tid, String payMethod, String cardName, String cardNo,
                                    String applNo, String resultCode) {
        boolean allUpdated = true;
        for (PaymentRespDTO.PaymentDTO payment : payments) {
            boolean updated = confirmPaid(payment, product, tid, payMethod, cardName, cardNo, applNo, resultCode);
            allUpdated = allUpdated && updated;
        }
        return allUpdated;
    }

    /** 형제 묶음결제 실패 확정 — confirmFailed를 그룹 전체에 대해 한 트랜잭션으로 반복한다 */
    @Transactional
    public void confirmFailedGroup(List<String> orderNos, String resultCode) {
        for (String orderNo : orderNos) {
            confirmFailed(orderNo, resultCode);
        }
    }

    /** 승인 시도 없이 이탈 확정 — X버튼/뒤로가기/방치. "안 샀다"와 "실패했다"를 구분하기 위해 나눈다 */
    @Transactional
    public void confirmClosed(String orderNo) {
        paymentRepository.markClosed(orderNo);
    }

    /** 형제 묶음결제 이탈 확정 — confirmClosed를 그룹 전체에 대해 한 트랜잭션으로 반복한다 */
    @Transactional
    public void confirmClosedGroup(List<String> orderNos) {
        for (String orderNo : orderNos) {
            confirmClosed(orderNo);
        }
    }

    /**
     * 환불 요청 기록 — PG 호출 전에 남긴다. 호출 중 서버가 죽어도 요청 흔적은 남아야
     * "환불 신청은 들어왔는데 처리가 안 된 건"을 나중에 찾아낼 수 있다.
     *
     * @return 생성된 cancel_id
     */
    @Transactional
    public int openCancel(int paymentId, int cancelAmount, String reason, String requestedBy,
                          String ruleCode, int usedDays, int usedCount) {
        PaymentRespDTO.CancelRowDTO row = new PaymentRespDTO.CancelRowDTO();
        row.setPaymentId(paymentId);
        row.setCancelAmount(cancelAmount);
        row.setReason(reason);
        row.setRequestedBy(requestedBy);
        row.setRuleCode(ruleCode);
        row.setUsedDays(usedDays);
        row.setUsedCount(usedCount);

        paymentRepository.insertCancelRequest(row);
        return row.getCancelId();
    }

    /**
     * 환불 성공 반영 — 누적 환불액 갱신과 이용권 회수를 한 트랜잭션으로 묶는다.
     * 돈은 돌려줬는데 횟수가 그대로 남아 있으면 공짜로 쓰게 되므로 갈라지면 안 된다.
     */
    @Transactional
    public void confirmRefunded(int paymentId, int cancelId, int cancelAmount, Integer passId,
                                String cancelTid, String resultCode, String resultMsg) {
        paymentRepository.updateCancelResult(cancelId, "DONE", cancelTid, resultCode, resultMsg);
        paymentRepository.applyRefund(paymentId, cancelAmount);
        if (passId != null) {
            passService.revoke(passId);
        }
    }

    /** 환불 실패 반영 — 금액은 건드리지 않고 실패 사유만 남긴다 */
    @Transactional
    public void confirmRefundFailed(int cancelId, String resultCode, String resultMsg) {
        paymentRepository.updateCancelResult(cancelId, "FAIL", null, resultCode, resultMsg);
    }
}
