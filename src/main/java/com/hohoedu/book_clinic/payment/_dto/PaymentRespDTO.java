package com.hohoedu.book_clinic.payment._dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 결제 응답 DTO — 서버가 앱/화면으로 내려주는 값들과, MyBatis 조회 결과를 담는 타입들.
 */
public class PaymentRespDTO {

    /**
     * 결제 시작 응답 — 앱이 이 값들로 이니시스 결제창을 띄운다.
     * signature 계열은 결제창 호출 규격이 앱 SDK 버전에 따라 달라서 여기서 만들지 않는다.
     * 서버가 책임지는 것은 "이 주문번호로 이 금액을 결제한다"까지다.
     */
    @Data
    public static class PrepareDTO {
        private final String orderNo;
        private final String mid;
        private final int amount;
        private final String productName;
        private final String returnUrl;
        private final String closeUrl;
        /** 테스트 상점이면 앱에서 "테스트 결제" 표시를 띄우게 한다 */
        private final boolean testMode;
    }

    /**
     * 형제 묶음결제 시작 응답 — prepare()의 그룹 버전.
     * amount/productName은 선택된 학생 전원의 합계·요약이다(개별 학생 상품은 항상 동일하다는 전제).
     */
    @Data
    public static class PrepareGroupDTO {
        private final String groupOrderNo;
        private final String mid;
        private final int amount;
        private final String productName;
        private final String returnUrl;
        private final String closeUrl;
        private final boolean testMode;
    }

    /** 승인 완료 응답 */
    @Data
    public static class ApproveDTO {
        private final int paymentId;
        private final String orderNo;
        private final String status;
        private final int amount;
        private final String cardName;
        /** 이 결제로 충전된 뒤의 잔여 횟수 */
        private final int remainCount;
    }

    /**
     * 환불 견적 — 규정을 적용하면 얼마가 나오는지 미리 보여준다.
     * 앱에서 "환불하시겠습니까? 3회 사용하셔서 50% 환불됩니다"를 띄우기 위한 것으로,
     * 실제 환불 실행과 같은 계산기를 쓴다(화면과 실제 금액이 달라지면 안 되므로).
     */
    @Data
    public static class RefundQuoteDTO {
        private final boolean refundable;
        /** 환불 불가 사유 (refundable=false일 때만) */
        private final String reason;
        private final String ruleCode;
        private final String ruleName;
        private final int usedDays;
        private final int usedCount;
        private final int refundAmount;
    }

    /** 결제 내역 한 줄 — 형제 묶음결제 도입 이후 조회 자체가 형제 그룹 전체를 합쳐서 나오므로,
     * 화면에서 "누구 결제인지" 구분할 수 있도록 studentId/studentName을 함께 내려준다. */
    @Data
    public static class HistoryDTO {
        private int paymentId;
        private String studentId;
        private String studentName;
        /** 형제 묶음결제일 때만 값이 있다 — 있으면 화면이 "환불" 시 형제 선택 체크박스를 띄운다 */
        private String groupOrderNo;
        private String orderNo;
        private String productName;
        private int amount;
        private int refundAmount;
        private String status;
        private String cardName;
        private String cardNo;
        private LocalDateTime requestedAt;
        private LocalDateTime paidAt;
    }

    // ───────────────────────────── 내부 조회용 ─────────────────────────────

    /** 상품 조회 결과 */
    @Data
    public static class ProductDTO {
        private int productId;
        private String productCode;
        private String productName;
        private String serviceCode;
        private int totalCount;
        private int price;
    }

    /** 결제 행 (승인/환불 로직이 참조하는 최소 컬럼) */
    @Data
    public static class PaymentDTO {
        private int paymentId;
        private String orderNo;
        /** 형제 묶음결제일 때만 값이 있다. 단일결제는 항상 null */
        private String groupOrderNo;
        /** 몇 월치 이용권인지(YYYYMM). prepare() 시점에 정해지고 승인 확정 때 그대로 이용권에 옮겨진다 */
        private String billingYm;
        private String tid;
        private String studentId;
        private String centerCode;
        private int productId;
        private String productName;
        private int amount;
        private int refundAmount;
        private String status;
        private LocalDateTime paidAt;
    }

    /**
     * 환불 요청 INSERT용 행 — 생성된 cancel_id를 돌려받아야 해서 DTO로 넘긴다.
     * @Param을 늘어놓으면 MyBatis가 생성키를 써넣을 대상이 없어 이후 결과 반영에서 어느 행을
     * 갱신할지 알 수 없게 된다.
     */
    @Data
    public static class CancelRowDTO {
        /** INSERT 후 MyBatis가 채워준다 */
        private Integer cancelId;
        private int paymentId;
        private int cancelAmount;
        private String reason;
        private String requestedBy;
        private String ruleCode;
        private int usedDays;
        private int usedCount;
    }

    /** 환불 규정 한 건 */
    @Data
    public static class RefundRuleDTO {
        private String ruleCode;
        private String ruleName;
        private int maxDays;
        private int maxCount;
        private int refundRate;
    }
}
