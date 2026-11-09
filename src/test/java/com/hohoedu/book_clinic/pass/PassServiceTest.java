package com.hohoedu.book_clinic.pass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.hohoedu.book_clinic.pass._dto.PassRespDTO;

/**
 * 예약 시 차감(2026-09-14)과 그에 딸린 90일 유효기간 확정/해제 로직.
 *
 * 이 두 가지가 이 서비스에서 조건부 UPDATE의 영향행수에 따라 분기하는 유일한 지점이라,
 * 실제 DB 없이도 "언제 활성화하고 언제 해제하는가"는 목으로 고정해둘 가치가 있다.
 */
@ExtendWith(MockitoExtension.class)
class PassServiceTest {

    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 9, 15);

    @Mock
    private PassRepository passRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PassService passService;

    private PassRespDTO.PassDTO pass(int passId, LocalDate validFrom) {
        PassRespDTO.PassDTO dto = new PassRespDTO.PassDTO();
        dto.setPassId(passId);
        dto.setValidFrom(validFrom);
        return dto;
    }

    private PassRespDTO.UseDTO use(int useId, int passId) {
        PassRespDTO.UseDTO dto = new PassRespDTO.UseDTO();
        dto.setUseId(useId);
        dto.setPassId(passId);
        return dto;
    }

    @Test
    void 첫_예약이면_그_회차_날짜부터_90일_당일까지로_기간이_확정된다() {
        when(passRepository.findUsablePassOn("S1", "BOOK", SERVICE_DATE)).thenReturn(pass(7, null));
        when(passRepository.activatePass(7, SERVICE_DATE, 90)).thenReturn(1);
        when(passRepository.decrementRemain(7)).thenReturn(1);

        assertTrue(passService.consumeForReservation("S1", "BOOK", 100L, SERVICE_DATE));

        verify(passRepository).activatePass(7, SERVICE_DATE, 90);
        verify(passRepository).insertUse(7, "S1", 100L, SERVICE_DATE);
    }

    @Test
    void 이미_기간이_정해진_이용권은_기간을_다시_건드리지_않는다() {
        when(passRepository.findUsablePassOn("S1", "BOOK", SERVICE_DATE))
                .thenReturn(pass(7, LocalDate.of(2026, 9, 1)));
        when(passRepository.decrementRemain(7)).thenReturn(1);

        assertTrue(passService.consumeForReservation("S1", "BOOK", 100L, SERVICE_DATE));

        verify(passRepository, never()).activatePass(anyInt(), any(), anyInt());
    }

    @Test
    void 그_날짜에_쓸_이용권이_없으면_차감_실패() {
        when(passRepository.findUsablePassOn("S1", "BOOK", SERVICE_DATE)).thenReturn(null);

        assertFalse(passService.consumeForReservation("S1", "BOOK", 100L, SERVICE_DATE));

        verify(passRepository, never()).decrementRemain(anyInt());
        verify(passRepository, never()).insertUse(anyInt(), any(), any(), any());
    }

    @Test
    void 첫_예약을_취소하면_확정된_기간도_다시_풀린다() {
        when(passRepository.findLiveUsesByReservation(100L)).thenReturn(List.of(use(1, 7)));
        when(passRepository.markUseCanceled(1)).thenReturn(1);
        when(passRepository.incrementRemain(7)).thenReturn(1);
        when(passRepository.countUse(7)).thenReturn(0);

        passService.restoreForReservation(100L);

        verify(passRepository).deactivatePass(7);
    }

    @Test
    void 다른_예약이_아직_그_이용권을_쓰고_있으면_기간은_유지된다() {
        when(passRepository.findLiveUsesByReservation(100L)).thenReturn(List.of(use(1, 7)));
        when(passRepository.markUseCanceled(1)).thenReturn(1);
        when(passRepository.incrementRemain(7)).thenReturn(1);
        when(passRepository.countUse(7)).thenReturn(2);

        passService.restoreForReservation(100L);

        verify(passRepository, never()).deactivatePass(anyInt());
    }

    @Test
    void 이미_복구된_차감은_두_번_되돌리지_않는다() {
        when(passRepository.findLiveUsesByReservation(100L)).thenReturn(List.of(use(1, 7)));
        when(passRepository.markUseCanceled(1)).thenReturn(0);

        passService.restoreForReservation(100L);

        verify(passRepository, never()).incrementRemain(eq(7));
    }

    // ── 서비스 제공기간 / 쿠폰 (2026-10-27) ──
    // 예시 학생: 10/1 결제 → 서비스 제공기간 12/30까지, 결제일 + 30일 = 10/31

    private static final LocalDate PAID = LocalDate.of(2026, 10, 1);

    private PassRespDTO.PassDTO appPass(int passId, LocalDate validFrom, LocalDate validUntil, int remain) {
        PassRespDTO.PassDTO dto = pass(passId, validFrom);
        dto.setStudentId("S1");
        dto.setProductId(1);
        dto.setServiceCode("BOOK");
        dto.setSource(PassService.SOURCE_PG);
        dto.setBillingYm("202610");
        dto.setValidUntil(validUntil);
        dto.setServiceUntil(PAID.plusDays(90));
        dto.setGrantedAt(PAID.atTime(10, 0));
        dto.setTotalCount(12);
        dto.setRemainCount(remain);
        return dto;
    }

    @Test
    void 결제_90일_당일이_서비스_제공기간_마지막_날이다() {
        assertEquals(LocalDate.of(2026, 12, 30), PAID.plusDays(PassService.SERVICE_DAYS));
    }

    @Test
    void 첫_예약_회차가_결제일_30일_이내면_그_회차부터_시작한다() {
        LocalDate oct15 = LocalDate.of(2026, 10, 15);
        assertEquals(oct15, PassService.activationStart(appPass(7, null, null, 12), oct15));
    }

    @Test
    void 첫_예약_회차가_결제일_30일보다_늦으면_시작일을_결제일_30일로_당긴다() {
        LocalDate nov20 = LocalDate.of(2026, 11, 20);
        assertEquals(LocalDate.of(2026, 10, 31), PassService.activationStart(appPass(7, null, null, 12), nov20));
    }

    @Test
    void 서비스_기간이_끝나면_남은_횟수만큼_쿠폰이_나오고_기간은_유효기간_끝까지다() {
        // 10/15 첫 예약 → 유효기간 1/13까지. 12/30에 4회 남음 → 쿠폰 4장, 12/31~1/13
        when(passRepository.lockPass(7)).thenReturn(appPass(7, LocalDate.of(2026, 10, 15), LocalDate.of(2027, 1, 13), 4));

        assertEquals(4, passService.convertToCoupon(7));

        verify(passRepository).markConverted(7);
        verify(passRepository).insertPass("S1", null, 1, "BOOK", PassService.SOURCE_COUPON, "7", "202610",
                LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 13), null, 4);
    }

    @Test
    void 유효기간이_서비스_기간보다_늦게_끝나지_않으면_쿠폰_없이_소멸한다() {
        when(passRepository.lockPass(7)).thenReturn(appPass(7, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 30), 3));

        assertEquals(0, passService.convertToCoupon(7));

        verify(passRepository).markConverted(7);
        verify(passRepository, never()).insertPass(any(), any(), anyInt(), any(), any(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void 이미_전환된_이용권은_다시_전환하지_않는다() {
        PassRespDTO.PassDTO converted = appPass(7, LocalDate.of(2026, 10, 15), LocalDate.of(2027, 1, 13), 0);
        converted.setConvertedAt(LocalDateTime.of(2026, 12, 31, 0, 5));
        when(passRepository.lockPass(7)).thenReturn(converted);

        assertEquals(0, passService.convertToCoupon(7));

        verify(passRepository, never()).markConverted(anyInt());
    }

    @Test
    void 소멸한_이용권의_예약을_취소하면_쿠폰_1장으로_돌려준다() {
        PassRespDTO.PassDTO converted = appPass(7, LocalDate.of(2026, 10, 15), LocalDate.of(2027, 1, 13), 0);
        converted.setConvertedAt(LocalDateTime.of(2026, 12, 31, 0, 5));
        PassRespDTO.PassDTO coupon = new PassRespDTO.PassDTO();
        coupon.setPassId(70);

        when(passRepository.findLiveUsesByReservation(100L)).thenReturn(List.of(use(1, 7)));
        when(passRepository.markUseCanceled(1)).thenReturn(1);
        when(passRepository.incrementRemain(7)).thenReturn(0);
        when(passRepository.lockPass(7)).thenReturn(converted);
        when(passRepository.findCouponOf("7")).thenReturn(coupon);

        passService.restoreForReservation(100L);

        verify(passRepository).addToCoupon(70, 1);
    }

    @Test
    void 환불로_회수된_이용권의_예약_취소는_돌려주지_않는다() {
        PassRespDTO.PassDTO revoked = appPass(7, LocalDate.of(2026, 10, 15), LocalDate.of(2027, 1, 13), 0);
        revoked.setRevokedAt(LocalDateTime.of(2026, 11, 1, 10, 0));

        when(passRepository.findLiveUsesByReservation(100L)).thenReturn(List.of(use(1, 7)));
        when(passRepository.markUseCanceled(1)).thenReturn(1);
        when(passRepository.incrementRemain(7)).thenReturn(0);
        when(passRepository.lockPass(7)).thenReturn(revoked);

        passService.restoreForReservation(100L);

        verify(passRepository, never()).addToCoupon(anyInt(), anyInt());
        verify(passRepository, never()).findCouponOf(any());
    }
}
