package com.hohoedu.book_clinic.pass;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    void 첫_예약이면_그_회차_날짜부터_90일로_기간이_확정된다() {
        when(passRepository.findUsablePassOn("S1", "BOOK", SERVICE_DATE)).thenReturn(pass(7, null));
        when(passRepository.activatePass(7, SERVICE_DATE, 89)).thenReturn(1);
        when(passRepository.decrementRemain(7)).thenReturn(1);

        assertTrue(passService.consumeForReservation("S1", "BOOK", 100L, SERVICE_DATE));

        verify(passRepository).activatePass(7, SERVICE_DATE, 89);
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
}
