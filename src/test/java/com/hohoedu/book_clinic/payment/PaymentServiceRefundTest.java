package com.hohoedu.book_clinic.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hohoedu.book_clinic.clinic.ClinicRepository;
import com.hohoedu.book_clinic.pass.PassService;
import com.hohoedu.book_clinic.pass._dto.PassRespDTO;
import com.hohoedu.book_clinic.payment._dto.PaymentRespDTO;
import com.hohoedu.book_clinic.payment.inicis.InicisClient;
import com.hohoedu.book_clinic.payment.inicis.InicisProperties;
import com.hohoedu.book_clinic.student.StudentRepository;
import com.hohoedu.book_clinic.student.model.Student;

/**
 * 형제 묶음결제 행의 환불 시, PG 취소 요청에 실어 보내는 금액이 "이 행 하나"가 아니라
 * "그룹 전체"를 기준으로 계산되는지 확인한다. 이게 틀리면(이 행 하나 기준으로 남은 금액을
 * 0으로 오판하면) 전액취소로 오인해 tid에 걸린 형제 전원의 결제가 통째로 취소된다.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceRefundTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentTxService paymentTxService;

    @Mock
    private PassService passService;

    @Mock
    private InicisClient inicisClient;

    @Mock
    private InicisProperties props;

    @Mock
    private ClinicRepository clinicRepository;

    @Mock
    private StudentRepository studentRepository;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void refund_형제묶음결제_행_환불은_그룹_전체_잔액_기준으로_부분취소를_요청한다() {
        // 형제 2명이 각 10,000원씩 내 그룹 전체는 20,000원. 그중 한 명(paymentId=1)만 환불 요청.
        PaymentRespDTO.PaymentDTO myPayment = new PaymentRespDTO.PaymentDTO();
        myPayment.setPaymentId(1);
        myPayment.setOrderNo("BC_A");
        myPayment.setGroupOrderNo("BG1");
        myPayment.setStudentId("S1");
        myPayment.setTid("TID1");
        myPayment.setAmount(10_000);
        myPayment.setRefundAmount(0);
        myPayment.setStatus("PAID");
        myPayment.setPaidAt(LocalDateTime.now().minusDays(1));

        PaymentRespDTO.PaymentDTO siblingPayment = new PaymentRespDTO.PaymentDTO();
        siblingPayment.setPaymentId(2);
        siblingPayment.setOrderNo("BC_B");
        siblingPayment.setGroupOrderNo("BG1");
        siblingPayment.setStudentId("S2");
        siblingPayment.setTid("TID1");
        siblingPayment.setAmount(10_000);
        siblingPayment.setRefundAmount(0);
        siblingPayment.setStatus("PAID");

        when(paymentRepository.findById(1)).thenReturn(myPayment);
        when(paymentRepository.findByGroupOrderNo("BG1")).thenReturn(List.of(myPayment, siblingPayment));
        // requireOwnPayment는 이제 "본인 결제"가 아니라 "형제 그룹 소속 학생의 결제"인지 확인한다.
        // 여기서는 요청자(S1) 본인 결제를 환불하는 상황이라 형제 그룹에 본인만 있어도 충분하다.
        when(studentRepository.findSiblingGroup("S1"))
                .thenReturn(List.of(Student.builder().studentId("S1").build()));

        // 잔여 12회 × 3,000원 = 36,000원이지만 이 행의 결제 금액(10,000원)을 넘을 수 없다
        PassRespDTO.PassDTO pass = new PassRespDTO.PassDTO();
        pass.setPassId(42);
        pass.setRemainCount(12);
        when(passService.findByRef(PassService.SOURCE_PG, "BC_A")).thenReturn(pass);
        when(passService.usedCount(42)).thenReturn(0);

        // 환불 선점(claimRefund)은 동시 요청 방지용 원자적 UPDATE다. 모킹이 없으면 0(=이미 선점됨)이
        // 돌아와 실제 환불 로직에 들어가기 전에 막힌다.
        when(paymentRepository.claimRefund(1)).thenReturn(1);

        when(paymentTxService.openCancel(eq(1), eq(10_000), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(99);

        InicisClient.Result okResult = new InicisClient.Result(200, Map.of("resultCode", "00"), "{}");
        when(inicisClient.refund(any(), any(), any(), anyInt())).thenReturn(okResult);

        paymentService.refund("S1", 1, "테스트 환불", "APP");

        // 그룹 전체 20,000원 중 이번에 10,000원만 취소하는 것이므로 "부분취소"여야 하고,
        // 취소 후 남을 잔액은 다른 형제의 10,000원이어야 한다(이 행만 봤다면 0으로 계산돼 전액취소가 됐을 것).
        ArgumentCaptor<Integer> partialAmountCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> remainAfterCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(inicisClient).refund(eq("TID1"), any(), partialAmountCaptor.capture(), remainAfterCaptor.capture());

        assertEquals(10_000, partialAmountCaptor.getValue(), "부분취소 금액은 이 학생 몫만큼이어야 한다");
        assertEquals(10_000, remainAfterCaptor.getValue(), "취소 후 남을 잔액은 다른 형제 몫(10,000원)이어야 한다");
    }

    /**
     * 72,000원 형제 묶음결제에서 동생(36,000원)이 먼저 환불한 뒤 형이 환불하는 경우(2026-10-22) —
     * 취소 후 남는 금액은 0원이지만 이미 부분취소된 거래라 전체취소가 아니라 부분취소로 보내야 한다.
     * 전체취소로 보내면 이니시스가 "부분취소 원거래 취소불가"로 거절한다.
     */
    @Test
    void refund_형제묶음결제_다른_형제가_먼저_환불했으면_마지막도_부분취소() {
        PaymentRespDTO.PaymentDTO older = new PaymentRespDTO.PaymentDTO();
        older.setPaymentId(11);
        older.setOrderNo("BC_OLD");
        older.setGroupOrderNo("BG_SIB");
        older.setStudentId("S_OLD");
        older.setTid("TID_SIB");
        older.setAmount(36_000);
        older.setRefundAmount(0);
        older.setStatus("PAID");
        older.setPaidAt(LocalDateTime.now().minusDays(1));

        PaymentRespDTO.PaymentDTO younger = new PaymentRespDTO.PaymentDTO();
        younger.setPaymentId(12);
        younger.setOrderNo("BC_YOUNG");
        younger.setGroupOrderNo("BG_SIB");
        younger.setStudentId("S_YOUNG");
        younger.setTid("TID_SIB");
        younger.setAmount(36_000);
        younger.setRefundAmount(36_000); // 동생은 이미 전액 환불받음
        younger.setStatus("CANCELED");

        when(paymentRepository.findById(11)).thenReturn(older);
        when(paymentRepository.findByGroupOrderNo("BG_SIB")).thenReturn(List.of(older, younger));
        when(studentRepository.findSiblingGroup("S_OLD"))
                .thenReturn(List.of(Student.builder().studentId("S_OLD").build()));

        PassRespDTO.PassDTO pass = new PassRespDTO.PassDTO();
        pass.setPassId(77);
        pass.setRemainCount(12);
        when(passService.findByRef(PassService.SOURCE_PG, "BC_OLD")).thenReturn(pass);
        when(passService.usedCount(77)).thenReturn(0);
        when(paymentRepository.claimRefund(11)).thenReturn(1);
        when(paymentTxService.openCancel(eq(11), eq(36_000), any(), any(), any(), anyInt(), anyInt())).thenReturn(101);
        InicisClient.Result okResult = new InicisClient.Result(200, Map.of("resultCode", "00"), "{}");
        when(inicisClient.refund(any(), any(), any(), anyInt())).thenReturn(okResult);

        paymentService.refund("S_OLD", 11, "테스트 환불", "APP");

        ArgumentCaptor<Integer> partialAmountCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> remainAfterCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(inicisClient).refund(eq("TID_SIB"), any(), partialAmountCaptor.capture(), remainAfterCaptor.capture());
        assertEquals(36_000, partialAmountCaptor.getValue(), "전체취소(null)가 아니라 부분취소 금액이 실려야 한다");
        assertEquals(0, remainAfterCaptor.getValue(), "형제 둘 다 환불하면 남는 금액은 0원");
    }

    // ── 환불 정책(2026-10-21): 남은 이용권 1회당 3,000원 / 기간 = 서비스 제공기간(결제일 + 90일 당일까지, 2026-10-27) ──

    private PaymentRespDTO.RefundQuoteDTO quoteFor(LocalDateTime paidAt, int remainCount) {
        PaymentRespDTO.PaymentDTO payment = new PaymentRespDTO.PaymentDTO();
        payment.setPaymentId(7);
        payment.setOrderNo("BC_Q");
        payment.setStudentId("S1");
        payment.setTid("TID7");
        payment.setAmount(36_000);
        payment.setRefundAmount(0);
        payment.setStatus("PAID");
        payment.setPaidAt(paidAt);
        when(paymentRepository.findById(7)).thenReturn(payment);
        when(studentRepository.findSiblingGroup("S1"))
                .thenReturn(List.of(Student.builder().studentId("S1").build()));

        PassRespDTO.PassDTO pass = new PassRespDTO.PassDTO();
        pass.setPassId(70);
        pass.setTotalCount(12);
        pass.setRemainCount(remainCount);
        when(passService.findByRef(PassService.SOURCE_PG, "BC_Q")).thenReturn(pass);
        when(passService.usedCount(70)).thenReturn(12 - remainCount);

        return paymentService.quote("S1", 7);
    }

    @Test
    void quote_미사용이면_12회_곱하기_3000원_전액() {
        PaymentRespDTO.RefundQuoteDTO quote = quoteFor(LocalDateTime.now(), 12);
        assertTrue(quote.isRefundable());
        assertEquals(36_000, quote.getRefundAmount());
    }

    @Test
    void quote_잔여_5회면_15000원() {
        PaymentRespDTO.RefundQuoteDTO quote = quoteFor(LocalDateTime.now().minusMonths(1), 5);
        assertTrue(quote.isRefundable());
        assertEquals(15_000, quote.getRefundAmount());
        assertEquals(7, quote.getUsedCount());
    }

    @Test
    void quote_결제일로부터_90일_당일까지는_환불된다() {
        PaymentRespDTO.RefundQuoteDTO quote = quoteFor(LocalDateTime.now().minusDays(90), 4);
        assertTrue(quote.isRefundable());
        assertEquals(12_000, quote.getRefundAmount());
    }

    @Test
    void quote_90일이_지나면_환불_불가() {
        PaymentRespDTO.RefundQuoteDTO quote = quoteFor(LocalDateTime.now().minusDays(91), 10);
        assertFalse(quote.isRefundable());
        assertEquals(0, quote.getRefundAmount());
    }

    @Test
    void quote_남은_횟수가_없으면_환불_불가() {
        PaymentRespDTO.RefundQuoteDTO quote = quoteFor(LocalDateTime.now(), 0);
        assertFalse(quote.isRefundable());
    }
}
