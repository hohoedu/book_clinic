package com.hohoedu.book_clinic.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        PassRespDTO.PassDTO pass = new PassRespDTO.PassDTO();
        pass.setPassId(42);
        when(passService.findByRef(PassService.SOURCE_PG, "BC_A")).thenReturn(pass);
        when(passService.usedCount(42)).thenReturn(0);

        PaymentRespDTO.RefundRuleDTO fullRefundRule = new PaymentRespDTO.RefundRuleDTO();
        fullRefundRule.setRuleCode("R100");
        fullRefundRule.setRuleName("전액환불");
        fullRefundRule.setMaxDays(9999);
        fullRefundRule.setMaxCount(0);
        fullRefundRule.setRefundRate(100);
        when(paymentRepository.findActiveRefundRules()).thenReturn(List.of(fullRefundRule));

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
}
