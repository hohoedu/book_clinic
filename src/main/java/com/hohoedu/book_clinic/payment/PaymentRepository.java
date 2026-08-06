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
                     @Param("productName") String productName, @Param("billingYm") String billingYm,
                     @Param("amount") int amount);

    PaymentRespDTO.PaymentDTO findByOrderNo(@Param("orderNo") String orderNo);

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
     * 오래 방치된 READY 주문 일괄 정리. 앱의 abandon 호출이 닿지 못한 경우(강제 종료,
     * 네트워크 끊김)의 최종 방어선이라 클라이언트 신호와 무관하게 시간만으로 판단한다.
     * 승인 시도 자체가 없었던 건만 걸리므로 CLOSED로 내린다.
     * @return 정리된 건수
     */
    int markStaleReadyAsClosed(@Param("olderThan") java.time.LocalDateTime olderThan);

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
}
