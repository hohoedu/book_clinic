package com.hohoedu.book_clinic.payment;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hohoedu.book_clinic.payment._dto.PaymentRespDTO;

/**
 * 결제/환불 MyBatis 매퍼 — erp_bookstore_product / payment / payment_cancel /
 * payment_log / refund_rule 접근.
 *
 * 이용권(erp_bookstore_pass, pass_use)은 여기서 다루지 않는다. 이용권은 PG 결제뿐 아니라
 * 서당 일괄청구로도 생기므로 결제 매퍼에 묶으면 서당 코드가 결제 코드를 거쳐 가게 된다.
 * PassRepository가 그 도메인을 단독으로 소유한다.
 */
@Mapper
public interface PaymentRepository {

    /** 판매중인 상품 목록 — 앱 결제 화면에 뿌린다 */
    List<PaymentRespDTO.ProductDTO> findActiveProducts(@Param("serviceCode") String serviceCode);

    /** 판매중인 상품 조회 (없으면 null) — 결제를 시작할 수 있는지 판단할 때 쓴다 */
    PaymentRespDTO.ProductDTO findActiveProduct(@Param("productCode") String productCode);

    /**
     * 상품 조회 (판매중 여부 무시). 이미 시작된 결제를 확정할 때는 이쪽을 쓴다.
     * 결제가 진행되는 사이 본사가 상품을 내렸다고 해서 이미 승인된 결제를 되돌리면,
     * 고객 입장에서는 아무 잘못 없이 결제가 취소되는 셈이기 때문이다.
     */
    PaymentRespDTO.ProductDTO findProductById(@Param("productId") int productId);

    /**
     * 결제 시작 — status=READY 행을 먼저 만든다. 생성된 payment_id를 DTO에 되돌려 받는다.
     * groupOrderNo는 형제 묶음결제일 때만 값이 있다(단일결제는 null로 넘긴다).
     */
    void insertReady(@Param("orderNo") String orderNo, @Param("groupOrderNo") String groupOrderNo,
                     @Param("studentId") String studentId,
                     @Param("centerCode") String centerCode, @Param("productId") int productId,
                     @Param("productName") String productName, @Param("serviceCode") String serviceCode,
                     @Param("billingYm") String billingYm, @Param("amount") int amount);

    PaymentRespDTO.PaymentDTO findByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 이 학생·서비스·청구월에 진행 중(READY)이거나 완료(PAID)된 결제가 이미 있는지 — 다른
     * 기기로 동시에 새 결제를 시작하려 할 때 중복 방지용으로 prepare()/prepareGroup()이 먼저
     * 확인한다(2026-08-07). 없으면 null. DB의 UX_payment_active_billing 필터드 유니크 인덱스가
     * 이 체크를 통과한 뒤에도 남는 아주 좁은 동시 요청 레이스의 최종 방어선이다.
     */
    PaymentRespDTO.PaymentDTO findActiveByStudentServiceBilling(@Param("studentId") String studentId,
            @Param("serviceCode") String serviceCode, @Param("billingYm") String billingYm);

    /** 같은 그룹으로 묶인 형제 묶음결제 행 전체 — 그룹 승인 확정 때 학생별로 순회하기 위함 */
    List<PaymentRespDTO.PaymentDTO> findByGroupOrderNo(@Param("groupOrderNo") String groupOrderNo);

    /**
     * 이 tid가 "이번 확정 대상이 아닌" 다른 행에 이미 쓰였는지 센다.
     * 단일결제(groupOrderNo=null)는 같은 tid를 가진 다른 행이 하나라도 있으면 안 되고,
     * 형제 묶음결제는 같은 group_order_no 안에서만 tid 공유가 허용된다 — 그 밖으로 새면 이상 징후다.
     * (tid 자체의 DB UNIQUE 제약은 형제 묶음결제와 양립할 수 없어 제거했고, 이 조회가 그 대체 방어선이다)
     */
    int countByTidElsewhere(@Param("tid") String tid, @Param("orderNo") String orderNo,
                            @Param("groupOrderNo") String groupOrderNo);

    PaymentRespDTO.PaymentDTO findById(@Param("paymentId") int paymentId);

    /**
     * 승인 확정 — READY 상태일 때만 PAID로 바꾼다.
     * WHERE에 status='READY'를 두는 것이 중복 승인 방어의 마지막 선이다. 앱이 같은 승인을
     * 두 번 보내면 두 번째 UPDATE는 0행이 되고, 호출부는 그걸 보고 재시도임을 안다.
     */
    int markPaid(@Param("orderNo") String orderNo, @Param("tid") String tid,
                 @Param("payMethod") String payMethod, @Param("cardName") String cardName,
                 @Param("cardNo") String cardNo, @Param("applNo") String applNo,
                 @Param("resultCode") String resultCode);

    /** 승인 실패·망취소 확정 — 이니시스가 실제로 응답을 준(거절·오류) 경우에만 쓴다 */
    int markFailed(@Param("orderNo") String orderNo, @Param("resultCode") String resultCode);

    /**
     * 승인을 시도조차 안 하고 이탈한 결제를 CLOSED로 내린다.
     * X버튼·뒤로가기·앱 강제종료·방치가 여기 해당한다. "실패"가 아니라 "안 샀다"이므로
     * FAILED와 구분한다 — 화면·로그에서 실제 장애를 사용자 단순 이탈과 섞어보지 않기 위해서다.
     */
    int markClosed(@Param("orderNo") String orderNo);

    /**
     * 오래 방치된 READY 주문 목록. 앱의 abandon 호출이 닿지 못한 경우(강제 종료, 네트워크 끊김)의
     * 최종 방어선이라 클라이언트 신호와 무관하게 시간만으로 판단한다. 예전엔 여기서 바로
     * CLOSED로 일괄 정리했지만, "승인 시도 자체가 없었다"는 게 사실은 추정일 뿐이었다 — 카드사
     * 승인은 났는데 콜백만 유실된 경우도 똑같이 방치돼 보이기 때문이다. 그래서 지금은 목록만
     * 받아 PaymentCleanupJob이 건별로 이니시스에 거래조회부터 해보고 정리한다(2026-08-07).
     */
    List<PaymentRespDTO.PaymentDTO> findStaleReady(@Param("olderThan") java.time.LocalDateTime olderThan);

    /** 환불 반영 — 누적 환불액을 더하고, 전액이 되면 CANCELED로 내린다 */
    int applyRefund(@Param("paymentId") int paymentId, @Param("cancelAmount") int cancelAmount);

    /** 환불 요청 기록 (status=REQ). 생성된 cancel_id는 인자로 넘긴 DTO에 채워진다 */
    void insertCancelRequest(PaymentRespDTO.CancelRowDTO row);

    /** 환불 결과 확정 */
    int updateCancelResult(@Param("cancelId") int cancelId, @Param("status") String status,
                           @Param("cancelTid") String cancelTid, @Param("resultCode") String resultCode,
                           @Param("resultMsg") String resultMsg);

    /**
     * PG 통신 원문 적재. 승인/취소는 성공이든 실패든 무조건 남긴다 —
     * 남기지 않은 호출은 분쟁 시 없었던 일이 된다.
     */
    void insertLog(@Param("orderNo") String orderNo, @Param("tid") String tid,
                   @Param("logType") String logType, @Param("httpStatus") Integer httpStatus,
                   @Param("resultCode") String resultCode, @Param("reqBody") String reqBody,
                   @Param("resBody") String resBody);

    /** 현행 환불 규정 목록 (priority 오름차순). 적용은 조건에 처음 맞는 한 건만 */
    List<PaymentRespDTO.RefundRuleDTO> findActiveRefundRules();

    /**
     * 결제 내역 (최신순) — 형제 그룹 전체(본인 포함)를 한 번에 조회한다.
     * 형제가 없는 학생은 studentIds가 본인 1건뿐이라 기존과 동일하게 본인 내역만 나온다.
     */
    List<PaymentRespDTO.HistoryDTO> findHistory(@Param("studentIds") List<String> studentIds);

    /** 형제 묶음결제 그룹 하나에 속한 결제 행 전체 — 환불 화면의 형제 선택 체크박스용 */
    List<PaymentRespDTO.HistoryDTO> findHistoryByGroupOrderNo(@Param("groupOrderNo") String groupOrderNo);

    /**
     * 운영자 수동 확인 필요로 표시 — 금액 불일치·망취소 실패·승인 확정 실패 등 코드가 스스로
     * 못 끝내는 지점마다 호출한다(2026-08-07). 이미 표시된 건에 또 걸리면 사유만 최신으로 덮는다.
     */
    void markNeedsReview(@Param("orderNo") String orderNo, @Param("reason") String reason);

    /** 확인 필요 목록 (미해결만, 최신순) — /admin/payment/review-view가 보여준다 */
    List<PaymentRespDTO.ReviewDTO> findNeedsReview();

    /** 운영자가 처리 완료로 표시 — reviewed_at을 채워 목록에서 빠지게 한다 */
    int resolveReview(@Param("paymentId") int paymentId);

    /**
     * 환불 선점 — 원자적 UPDATE로 refund_requested_at을 채운다. 이미 선점됐거나(다른 요청이
     * 처리 중) 이미 환불된(refund_amount>0) 건이면 0행 갱신되어 경합을 막는다.
     * markPaid의 WHERE status='READY'와 같은 원리(2026-08-07).
     * @return 선점 성공하면 1, 실패(이미 처리 중/완료)면 0
     */
    int claimRefund(@Param("paymentId") int paymentId);

    /** 환불 선점 해제 — 성공/실패 무관하게 refund() 종료 시 항상 호출한다(재시도 가능하게) */
    void releaseRefundClaim(@Param("paymentId") int paymentId);

    /**
     * 최근 CLOSED로 닫힌 주문 중 아직 사후 재확인 안 한 것 — "닫았는데 사실은 승인됐던" 레이스를
     * 잡는 용도(2026-08-07). windowStart보다 오래된 CLOSED 건은 대상에서 제외한다 — 오래전에
     * 닫힌 주문까지 매번 다시 조회하는 건 낭비고, 이 레이스는 애초에 닫힌 직후에만 일어난다.
     */
    List<PaymentRespDTO.PaymentDTO> findClosedForRecheck(@Param("windowStart") java.time.LocalDateTime windowStart);

    /** CLOSED 사후 재확인 완료 표시 — 다시 검사 대상에서 빠진다 */
    void markClosedRechecked(@Param("orderNo") String orderNo);
}
