package com.hohoedu.book_clinic.schedule.materialize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hohoedu.book_clinic._core.utils.KstClock;
import com.hohoedu.book_clinic.schedule._dto.MaterializeDTO;

/** 휴무 요일을 하루짜리 운영시간 변경 예외로 그날만 여는지(2026-11-19) */
@ExtendWith(MockitoExtension.class)
class ScheduleMaterializerClosedDayTest {

    private static final String CENTER = "C1";
    private final LocalDate date = KstClock.today().plusDays(3);

    @Mock SlotInstanceRepository repo;
    @InjectMocks ScheduleMaterializer materializer;

    private void given(LocalDate exStart, LocalDate exEnd) {
        MaterializeDTO.RuleDTO rule = new MaterializeDTO.RuleDTO();
        rule.setDayOfWeek(date.getDayOfWeek().getValue());
        rule.setEffectiveFrom(date.minusYears(1));
        rule.setIsOpen(false);   // 휴무 요일
        when(repo.findRulesUpTo(eq(CENTER), any())).thenReturn(List.of(rule));

        MaterializeDTO.ExceptionDTO ex = new MaterializeDTO.ExceptionDTO();
        ex.setExceptionId(1);
        ex.setStartDate(exStart);
        ex.setEndDate(exEnd);
        ex.setExceptionType("TIME_CHANGE");
        ex.setOpenTime(LocalTime.of(10, 0));
        ex.setCloseTime(LocalTime.of(12, 0));
        when(repo.findExceptionsInRange(eq(CENTER), any(), any())).thenReturn(List.of(ex));

        MaterializeDTO.ExceptionSlotDTO slot = new MaterializeDTO.ExceptionSlotDTO();
        slot.setExceptionId(1);
        slot.setSeq(1);
        slot.setStartTime(LocalTime.of(10, 0));
        slot.setEndTime(LocalTime.of(11, 0));
        slot.setCapacity(5);
        when(repo.findExceptionSlotsInRange(eq(CENTER), any(), any())).thenReturn(List.of(slot));
    }

    @Test
    @SuppressWarnings("unchecked")
    void 하루짜리_운영시간_변경이면_휴무_요일도_그날만_연다() {
        given(date, date);

        materializer.materializeRange(CENTER, date, date);

        ArgumentCaptor<List<MaterializeDTO.InstanceDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(repo).mergeSlots(eq(CENTER), eq(date), captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(5, captor.getValue().get(0).getCapacity());
    }

    @Test
    void 기간형_운영시간_변경은_휴무_요일을_열지_않는다() {
        given(date.minusDays(1), date.plusDays(1));

        materializer.materializeRange(CENTER, date, date);

        verify(repo, never()).mergeSlots(any(), any(), anyList());
    }
}
