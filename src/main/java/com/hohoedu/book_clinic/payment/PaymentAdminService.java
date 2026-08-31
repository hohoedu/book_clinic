package com.hohoedu.book_clinic.payment;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic.payment._dto.PaymentAdminRespDTO;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 "결제 내역" 화면 서비스 (2026-08-31). 읽기 전용이다.
 *
 * [뱃지 판정이 SQL이 아니라 여기 있는 이유] "이용중/이용 완료/미결제/부분 환불/환불 완료"는
 * 이용권 잔여·유효기간·결제 상태·환불액 네 가지를 함께 봐야 정해진다. CASE 식으로 SQL에 밀어넣으면
 * 조건 하나 바뀔 때마다 쿼리를 고쳐야 하고, 그 판정 순서(환불이 잔여보다 우선한다 등)를 화면과
 * 서버가 각자 해석하게 된다. 판정을 한 곳에 두고 화면은 내려온 값을 그리기만 한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentAdminService {

    /** 뱃지 코드 → 화면 라벨. 코드와 라벨을 같이 내리는 건 화면이 색(class)은 코드로, 글자는 라벨로 쓰기 때문 */
    private static final Map<String, String> STATUS_LABELS = Map.of(
            "IN_USE", "이용중",
            "USED_UP", "이용 완료",
            "UNPAID", "미결제",
            "PARTIAL_REFUND", "부분 환불",
            "REFUNDED", "환불 완료");

    private final PaymentAdminRepository paymentAdminRepository;

    /**
     * 목록 + 요약. billingYm은 화면의 month 입력값(YYYY-MM)이 아니라 DB 규격(YYYYMM)으로 받는다.
     * statusFilter는 뱃지 코드(IN_USE 등)이고, 비어 있으면 전체다.
     */
    @Transactional(readOnly = true)
    public PaymentAdminRespDTO.HistoryPageDTO getHistoryPage(String centerCode, String billingYm, String serviceCode,
                                                             String gradeKey, String statusFilter, String keyword) {
        List<PaymentAdminRespDTO.HistoryRowDTO> rows =
                paymentAdminRepository.findHistoryRows(centerCode, billingYm, serviceCode, gradeKey, keyword);

        LocalDate today = LocalDate.now();
        rows.forEach(row -> {
            String status = resolvePassStatus(row, today);
            row.setPassStatus(status);
            row.setPassStatusLabel(STATUS_LABELS.get(status));
        });

        // 요약("결제완료 89명 | 미결제 11명")은 상태 필터를 적용하기 전 전체 기준으로 센다 —
        // 필터를 걸 때마다 총원이 달라지면 "이번 달 몇 명이 안 냈나"라는 원래 질문에 답할 수 없다.
        int unpaid = (int) rows.stream().filter(r -> "UNPAID".equals(r.getPassStatus())).count();

        List<PaymentAdminRespDTO.HistoryRowDTO> visible = rows;
        if (statusFilter != null && !statusFilter.isBlank()) {
            if (!STATUS_LABELS.containsKey(statusFilter)) {
                throw new Exception400("알 수 없는 결제 상태입니다: " + statusFilter);
            }
            visible = rows.stream().filter(r -> statusFilter.equals(r.getPassStatus())).toList();
        }

        return PaymentAdminRespDTO.HistoryPageDTO.builder()
                .billingYm(billingYm)
                .paidStudentCount(rows.size() - unpaid)
                .unpaidStudentCount(unpaid)
                .rows(visible)
                .build();
    }

    /**
     * 뱃지 판정. 순서가 곧 정책이다.
     *
     *  1) 환불이 먼저다 — 환불된 건은 잔여가 0으로 회수돼 있어 뒤에 두면 "이용 완료"로 묻힌다.
     *     전액 환불(status=CANCELED)과 부분 환불(PAID + refund_amount>0)을 나눈다.
     *  2) 결제가 확정되지 않았으면 미결제 — 이용권만 있고 PG 결제 행이 없는 서당 청구분은
     *     이 화면(책방 이용료)에서는 결제 사실이 없는 것과 같다.
     *  3) 그 다음에야 잔여/유효기간을 본다. 잔여가 남아도 그 달이 지났으면 이월 없이 소멸이므로
     *     "이용 완료"다(월말 소멸 정책 — erp_bookstore_pass.valid_until).
     */
    private String resolvePassStatus(PaymentAdminRespDTO.HistoryRowDTO row, LocalDate today) {
        String paymentStatus = row.getPaymentStatus();
        int refundAmount = row.getRefundAmount() == null ? 0 : row.getRefundAmount();

        if ("CANCELED".equals(paymentStatus)) {
            return "REFUNDED";
        }
        if (refundAmount > 0) {
            return "PARTIAL_REFUND";
        }
        if (!"PAID".equals(paymentStatus)) {
            return "UNPAID";
        }
        boolean expired = row.getValidUntil() != null && row.getValidUntil().isBefore(today);
        boolean exhausted = row.getRemainCount() == null || row.getRemainCount() <= 0;
        return (expired || exhausted) ? "USED_UP" : "IN_USE";
    }

    /**
     * 행 펼침 상세 — 차감 내역 + 결제/환불 내역.
     *
     * passId/paymentId는 화면이 보내온 값이라 그대로 믿지 않는다. 이 화면의 목록 자체는
     * centerCode로 스코핑돼 있지만, 상세 API를 직접 호출하면 다른 센터 학생의 이용권 번호를
     * 찍어 넣을 수 있다. 두 키가 정말 그 학생 것인지 서버가 다시 확인한다(학생이 내 센터
     * 소속인지는 컨트롤러의 CenterAccessGuard가 이미 본다).
     */
    @Transactional(readOnly = true)
    public PaymentAdminRespDTO.HistoryDetailDTO getHistoryDetail(String studentId, Integer passId, Integer paymentId) {
        List<PaymentAdminRespDTO.PassUseRowDTO> passUses = List.of();
        if (passId != null) {
            requireOwner(studentId, paymentAdminRepository.findPassOwner(passId), "이용권");
            passUses = paymentAdminRepository.findPassUses(passId, studentId);
        }

        List<PaymentAdminRespDTO.PaymentTrailRowDTO> trail = List.of();
        if (paymentId != null) {
            requireOwner(studentId, paymentAdminRepository.findPaymentOwner(paymentId), "결제");
            trail = paymentAdminRepository.findPaymentTrail(paymentId);
        }

        return PaymentAdminRespDTO.HistoryDetailDTO.builder()
                .passUses(passUses)
                .trail(trail)
                .build();
    }

    private void requireOwner(String studentId, String ownerStudentId, String what) {
        if (ownerStudentId == null || !ownerStudentId.equals(studentId)) {
            throw new Exception400("해당 학생의 " + what + " 정보가 아닙니다.");
        }
    }
}
