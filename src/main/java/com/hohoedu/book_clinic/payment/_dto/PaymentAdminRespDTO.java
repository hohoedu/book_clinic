package com.hohoedu.book_clinic.payment._dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * 관리자 "결제 내역" 화면 응답 DTO (2026-08-31).
 *
 * [행의 단위가 결제가 아니라 "학생 × 이용월"인 이유] 이 화면이 답해야 하는 질문은
 * "이번 달 누가 냈고 누가 안 냈나"다. 결제 행만 훑으면 애초에 결제하지 않은 학생이
 * 목록에서 통째로 빠져 미결제가 보이지 않는다. 그래서 센터 재원생 전체를 기준선으로
 * 깔고 그 달 이용권/결제를 좌측 조인해 붙인다 — 결제 행이 없는 학생은 "미결제"로 남는다.
 *
 * [금액이 결제 행이 아니라 상품에서도 나오는 이유] 미결제 학생은 payment.amount가 없다.
 * 화면에는 "얼마를 내야 하는데 안 냈나"가 보여야 하므로, 결제 행이 없으면 이용권에 붙은
 * 상품 가격을 대신 쓴다(이용권도 없으면 금액은 비운다).
 */
public class PaymentAdminRespDTO {

    /** 목록 한 줄 = 학생 1명의 그 달 결제/이용권 상태 */
    @Data
    public static class HistoryRowDTO {
        private String studentId;
        private String studentName;
        private String gradeKey;
        private String gradeName;

        /** 그 달 이용권(pass_id). 없으면 null — 상세 펼침의 "차감 내역"이 비어 있게 된다 */
        private Integer passId;
        /** PG / SEODANG / FREE — 이용권이 어디서 왔는지 */
        private String passSource;
        private Integer totalCount;
        private Integer remainCount;
        private LocalDate validUntil;

        /** PG 결제 행(있을 때만). 상세 펼침의 "결제/환불 내역"이 이 값으로 조회된다 */
        private Integer paymentId;
        private String orderNo;
        private LocalDateTime paidAt;
        /** 결제 금액. 결제 행이 없으면 이용권 상품 가격, 그것도 없으면 null */
        private Integer amount;
        private Integer refundAmount;
        /** payment.status 원본 (PAID/CANCELED). 결제 행이 없으면 null */
        private String paymentStatus;

        /** 그 이용권에서 실제로 차감된 횟수(pass_use 행 수) */
        private int usedCount;

        /** 화면 뱃지 값 — PaymentAdminService.resolvePassStatus()가 정한다 */
        private String passStatus;
        private String passStatusLabel;
    }

    /** 목록 + 상단 요약("결제완료 89명 | 미결제 11명")을 한 번에 내린다 */
    @Data
    @Builder
    public static class HistoryPageDTO {
        private String billingYm;
        private int paidStudentCount;
        private int unpaidStudentCount;
        private List<HistoryRowDTO> rows;
    }

    /**
     * 차감 내역 한 줄 — 이용일/회차/차감 후 잔여.
     *
     * [회차를 조인이 아니라 순번으로 맞추는 이유] pass_use에는 slot_instance_id가 없다
     * (하루 1행 시절 설계라 used_date까지만 남는다). 지금은 "그날 출석 확정된 회차 수만큼"
     * 행이 생기므로(2026-08-28), 같은 날 안에서 차감 행 n번째 ↔ 그날 예약 회차 n번째로
     * 순번을 맞춰 시간을 붙인다. 예약이 지워졌거나 순번이 어긋나면 회차 칸만 비고
     * 이용일·잔여는 그대로 나온다.
     */
    @Data
    public static class PassUseRowDTO {
        private LocalDate usedDate;
        /** 회차 번호(1,2,3…). 짝지을 예약을 못 찾으면 null */
        private Integer slotSeq;
        private LocalDateTime startsAt;
        private LocalDateTime endsAt;
        /** 이 차감 직후의 잔여 횟수 */
        private int remainAfter;
        /** 예약 상태 (ATTENDED/RESERVED/NOSHOW). 못 찾으면 null */
        private String reservationStatus;
    }

    /** 결제/환불 내역 한 줄 — 결제 1건 + 그 건의 취소 이력들을 시간 역순으로 합친 것 */
    @Data
    public static class PaymentTrailRowDTO {
        /** PAY(결제) / CANCEL(취소) */
        private String trailType;
        private LocalDateTime occurredAt;
        /** 취소 행은 음수로 내려간다 (화면에서 -10,000원으로 그대로 찍힌다) */
        private int amount;
        /** PAY는 payment.status, CANCEL은 payment_cancel.status(REQ/DONE/FAIL) */
        private String status;
        /** 취소 실패 사유 (CANCEL + FAIL일 때만) */
        private String resultMsg;
    }

    /** 행 펼침 상세 — 차감 내역 + 결제/환불 내역 */
    @Data
    @Builder
    public static class HistoryDetailDTO {
        private List<PassUseRowDTO> passUses;
        private List<PaymentTrailRowDTO> trail;
    }
}
