package com.hohoedu.book_clinic.payment._dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 자동결제 요청 DTO — 2026-09-07.
 *
 * 금액은 어떤 요청에도 들어 있지 않다. 매월 빠질 금액은 상품 마스터에서 서버가 읽는다 —
 * 자동결제는 클라이언트가 한 번 보낸 값으로 매달 돈이 나가는 구조라, 금액을 입력으로 받으면
 * 위변조 한 번이 영구적인 피해가 된다.
 */
public class SubscriptionReqDTO {

    /** 카드등록 시작 — 본인만이면 studentIds에 자기 하나, 형제 합산이면 여럿 */
    @Data
    public static class CardRegDTO {
        @NotBlank(message = "학생 정보가 필요합니다.")
        private String studentId;

        @NotEmpty(message = "자동결제를 등록할 학생을 선택해주세요.")
        private List<String> studentIds;

        @NotBlank(message = "상품을 선택해주세요.")
        private String productCode;
    }

    /** 해지 */
    @Data
    public static class CancelDTO {
        @NotBlank(message = "학생 정보가 필요합니다.")
        private String studentId;

        private int subscriptionId;
    }

    /** 실패한 청구 재시도 — PAUSED 구독을 학부모가 직접 다시 돌린다 */
    @Data
    public static class RetryDTO {
        @NotBlank(message = "학생 정보가 필요합니다.")
        private String studentId;

        private int subscriptionId;
    }
}
