package com.hohoedu.book_clinic.payment._dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 결제 요청 DTO — 앱(Flutter)이 서버로 보내는 값들.
 *
 * 금액이 어디에도 없는 것이 의도다. 앱이 보낸 금액을 그대로 쓰면 위변조된 금액으로 결제가
 * 확정되므로, 금액은 항상 서버가 erp_bookstore_product에서 읽는다. 앱은 "무슨 상품을
 * 사겠다"까지만 말할 수 있다.
 */
public class PaymentReqDTO {

    /** 결제 시작 — 서버가 주문번호를 발급하고 READY 행을 만든다 */
    @Data
    public static class PrepareDTO {
        @NotEmpty(message = "학생 정보가 없습니다.")
        private String studentId;

        @NotEmpty(message = "상품을 선택해주세요.")
        private String productCode;
    }

    /** 승인 요청 — 앱이 이니시스 결제창 인증을 마치고 받은 값을 그대로 넘긴다 */
    @Data
    public static class ApproveDTO {
        @NotEmpty(message = "학생 정보가 없습니다.")
        private String studentId;

        @NotEmpty(message = "주문번호가 없습니다.")
        private String orderNo;

        @NotEmpty(message = "인증 토큰이 없습니다.")
        private String authToken;

        /** 이니시스가 내려준 승인 요청 주소. 서버가 이니시스 도메인인지 검증한 뒤 사용한다 */
        @NotEmpty(message = "승인 주소가 없습니다.")
        private String authUrl;

        /** 승인 후 실패 시 되돌릴 망취소 주소. 이것도 도메인 검증 대상이다 */
        private String netCancelUrl;
    }

    /**
     * 형제 묶음결제 시작 — prepare()의 그룹 버전. studentId는 로그인 학생(요청자) 본인이고,
     * siblingStudentIds는 실제로 결제 대상으로 선택된 학생 목록(본인 포함 여부는 앱이 정한다).
     * 서버는 siblingStudentIds가 studentId의 형제 그룹에 실제로 속하는지 다시 검증한다.
     */
    @Data
    public static class PrepareGroupDTO {
        @NotEmpty(message = "학생 정보가 없습니다.")
        private String studentId;

        private List<String> siblingStudentIds;

        @NotEmpty(message = "상품을 선택해주세요.")
        private String productCode;
    }

    /** 결제 포기 — 앱이 결제창을 닫을 때 보낸다 */
    @Data
    public static class AbandonDTO {
        @NotEmpty(message = "학생 정보가 없습니다.")
        private String studentId;

        @NotEmpty(message = "주문번호가 없습니다.")
        private String orderNo;
    }

    /** 환불 신청 — 규정은 서버가 적용하므로 앱은 금액을 정하지 않는다 */
    @Data
    public static class RefundDTO {
        @NotEmpty(message = "학생 정보가 없습니다.")
        private String studentId;

        private Integer paymentId;

        /** 사유. 비워두면 서버가 기본 문구를 넣는다(이니시스 필수 파라미터라 빈 값으로 보낼 수 없음) */
        private String reason;
    }
}
