package com.hohoedu.book_clinic.payment._dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 자동결제(구독) 응답 DTO — 2026-09-07 전환.
 *
 * [빌키는 여기 없다] bill_key는 그 자체로 과금이 가능한 자격증명이라, 화면으로 나가는 DTO에는
 * 절대 싣지 않는다. 서버 내부에서 승인 요청에 쓰는 값은 SubscriptionDTO(조회 행)에만 담기고,
 * 앱으로 내려가는 StatusDTO에는 카드사명과 마스킹 번호까지만 들어간다.
 */
public class SubscriptionRespDTO {

    /** 구독 행 — 서버 내부 조회 결과 */
    @Data
    public static class SubscriptionDTO {
        private int subscriptionId;
        /** 카드등록 결제창에 넘긴 주문번호. 결제창에서 돌아온 요청을 이 값으로 되짚는다 */
        private String regOrderNo;
        private String ownerStudentId;
        private String centerCode;
        /** 승인 요청에만 쓴다. 로그·응답 어디에도 내보내지 않는다 */
        private String billKey;
        private String cardName;
        private String cardNo;
        /** PENDING / ACTIVE / PAUSED / CANCELED */
        private String status;
        private Integer anchorDay;
        /** 다음 청구를 시도할 날짜 */
        private LocalDate nextBillingOn;
        /** 다음 청구가 커버할 주기의 시작일 — 실패 재시도로 시도일이 밀려도 이 값은 그대로다 */
        private LocalDate billingCycleFrom;
        /** 학부모가 고른 첫 결제일(E-1). PENDING 때 저장, 등록 확정 시 anchor_day/next_billing_on의 기준이 된다. NULL이면 등록일 */
        private LocalDate firstBillingOn;
        private LocalDateTime lastPaidAt;
        private int failCount;
        private String lastFailReason;
    }

    /** 구독에 묶인 청구 대상 학생 — 상품 정보까지 조인해서 가져온다 */
    @Data
    public static class MemberDTO {
        private int memberId;
        private int subscriptionId;
        private String studentId;
        private String studentName;
        private String centerCode;
        private int productId;
        private String serviceCode;
        private String status;
        /** 아래 셋은 상품 마스터의 현재 값이다(스냅샷이 아니라) — 청구 시점에 읽어 payment에 남긴다 */
        private String productName;
        private int price;
        private int totalCount;
    }

    /**
     * 카드등록창 파라미터 — 화면이 이 값들로 이니시스 모바일 빌키 발급창에 폼을 던진다.
     *
     * [금액이 없다] 빌키 발급 단계에서는 돈이 빠지지 않는다. 카드 본인확인만 하고, 실제 첫
     * 청구는 등록이 확정된 뒤 서버가 빌키로 따로 승인한다. monthlyAmount는 "매월 OO원이
     * 결제됩니다"를 사람에게 보여주기 위한 표시값일 뿐 결제창으로 나가지 않는다.
     *
     * [서명 파라미터가 없는 이유] 모바일 모듈은 PC 웹표준과 달리 signature/mKey를 쓰지 않는다.
     * 그래서 위변조 방어는 전적으로 서버 승인 단계에 있다 — 등록 확정은 우리가 발급한
     * 주문번호(P_NOTI로 되돌려받는다)로 구독을 찾아 처리한다.
     */
    @Data
    public static class CardRegDTO {
        private final String orderNo;
        private final String mid;
        private final String goodName;
        private final String buyerName;
        private final String returnUrl;
        private final String closeUrl;
        /** 매월 청구될 금액 — 화면 안내용 */
        private final int monthlyAmount;
        private final boolean testMode;
    }

    /** 앱의 자동결제 상태 화면 */
    @Data
    public static class StatusDTO {
        private final Integer subscriptionId;
        /** 구독이 아예 없으면 NONE */
        private final String status;
        private final String cardName;
        private final String cardNo;
        private final LocalDate nextBillingOn;
        private final int monthlyAmount;
        private final List<MemberSummaryDTO> members;
        /** PAUSED일 때 학부모에게 보여줄 사유 */
        private final String failReason;
    }

    /** 상태 화면에 나가는 학생 요약 */
    @Data
    public static class MemberSummaryDTO {
        private final String studentId;
        private final String studentName;
        private final String productName;
        private final int price;
    }

    /** 카드등록 완료 응답 — 첫 청구까지 끝난 결과 */
    @Data
    public static class CardRegResultDTO {
        private final int subscriptionId;
        private final String status;
        private final String cardName;
        private final String cardNo;
        private final LocalDate nextBillingOn;
        /** 첫 청구로 충전된 뒤의 잔여 횟수(대표 학생 기준) */
        private final int remainCount;
    }
}
