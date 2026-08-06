package com.hohoedu.book_clinic.payment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hohoedu.book_clinic.pass.PassService;
import com.hohoedu.book_clinic.payment._dto.PaymentRespDTO;

/**
 * UX_payment_tid 유니크 인덱스를 제거하면서(형제 묶음결제가 같은 tid를 여러 행에 정당하게
 * 나눠 쓰기 때문) 그 자리를 대신하게 된 애플리케이션 레벨 검증(countByTidElsewhere)이
 * 실제로 걸리는지 확인한다. 실제 이니시스 tid는 PG가 발급하므로 임의로 재현할 수 없어,
 * PaymentRepository를 목(mock)으로 대체해 "tid가 다른 주문/그룹에 이미 쓰인" 상황을 강제로 만든다.
 */
@ExtendWith(MockitoExtension.class)
class PaymentTxServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PassService passService;

    @InjectMocks
    private PaymentTxService paymentTxService;

    @Test
    void confirmPaid_단일결제_tid가_다른_주문에_이미_쓰였으면_예외() {
        PaymentRespDTO.PaymentDTO payment = new PaymentRespDTO.PaymentDTO();
        payment.setOrderNo("BC001");
        payment.setGroupOrderNo(null); // 단일결제

        PaymentRespDTO.ProductDTO product = new PaymentRespDTO.ProductDTO();

        // 이 tid를 가진 다른 행이 이미 존재한다고 시뮬레이션
        when(paymentRepository.countByTidElsewhere("SAME_TID", "BC001", null)).thenReturn(1);

        assertThrows(IllegalStateException.class, () -> paymentTxService.confirmPaid(
                payment, product, "SAME_TID", "Card", "국민카드", "123456******7890", "00000000", "0000"));

        // 이상 징후로 막혔으면 markPaid까지 가면 안 된다
        verify(paymentRepository, never()).markPaid(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void confirmPaid_형제묶음결제_같은_그룹안에서_tid_공유는_허용() {
        PaymentRespDTO.PaymentDTO payment = new PaymentRespDTO.PaymentDTO();
        payment.setOrderNo("BC002");
        payment.setGroupOrderNo("BG001"); // 형제 묶음결제 — 이미 그룹 내 다른 학생 행이 같은 tid를 씀

        PaymentRespDTO.ProductDTO product = new PaymentRespDTO.ProductDTO();
        product.setProductId(1);
        product.setServiceCode("BOOK");
        product.setTotalCount(10);

        // 같은 그룹(BG001) 안에서의 공유는 "다른 곳에 쓰인 것"으로 세지 않는다 → 0건
        when(paymentRepository.countByTidElsewhere("SHARED_TID", "BC002", "BG001")).thenReturn(0);
        when(paymentRepository.markPaid(eq("BC002"), eq("SHARED_TID"), any(), any(), any(), any(), any()))
                .thenReturn(1);

        boolean updated = paymentTxService.confirmPaid(
                payment, product, "SHARED_TID", "Card", "국민카드", "123456******7890", "00000000", "0000");

        assertTrue(updated);
        verify(passService).grant(any(), any(), anyInt(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void confirmPaid_형제묶음결제_그룹_밖에서_tid가_재사용되면_예외() {
        PaymentRespDTO.PaymentDTO payment = new PaymentRespDTO.PaymentDTO();
        payment.setOrderNo("BC003");
        payment.setGroupOrderNo("BG002");

        PaymentRespDTO.ProductDTO product = new PaymentRespDTO.ProductDTO();

        // 이 tid가 BG002 밖의(다른 그룹이거나 단일결제인) 행에 이미 쓰였다고 시뮬레이션
        when(paymentRepository.countByTidElsewhere("LEAKED_TID", "BC003", "BG002")).thenReturn(1);

        assertThrows(IllegalStateException.class, () -> paymentTxService.confirmPaid(
                payment, product, "LEAKED_TID", "Card", "국민카드", "123456******7890", "00000000", "0000"));

        verify(paymentRepository, never()).markPaid(any(), any(), any(), any(), any(), any(), any());
    }
}
