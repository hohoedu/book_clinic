package com.hohoedu.book_clinic.schedule.materialize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hohoedu.book_clinic._core.utils.KstClock;
import com.hohoedu.book_clinic.schedule._dto.MaterializeDTO;

/** 대체 운영일은 원래 쉬는 날이어도 미운영일과 같은 회차로 그날만 열린다 (2026-11-23) */
@ExtendWith(MockitoExtension.class)
class ScheduleMaterializerSubstituteTest {

    private static final String CENTER = "C1";
    private final LocalDate closedDay = KstClock.today().plusDays(5);
    private final LocalDate substitute = closedDay.plusDays(1);

    @Mock SlotInstanceRepository repo;
    @InjectMocks ScheduleMaterializer materializer;

    private MaterializeDTO.RuleDTO rule(LocalDate date, boolean open) {
        MaterializeDTO.RuleDTO r = new MaterializeDTO.RuleDTO();
        r.setDayOfWeek(date.getDayOfWeek().getValue());
        r.setEffectiveFrom(date.minusYears(1));
        r.setIsOpen(open);
        r.setDefaultCapacity(10);
        return r;
    }

    private MaterializeDTO.TemplateSlotDTO template(int seq, int hour) {
        MaterializeDTO.TemplateSlotDTO t = new MaterializeDTO.TemplateSlotDTO();
        t.setDayOfWeek(closedDay.getDayOfWeek().getValue());
        t.setEffectiveFrom(closedDay.minusYears(1));
        t.setSeq(seq);
        t.setStartTime(LocalTime.of(hour, 0));
        t.setEndTime(LocalTime.of(hour + 1, 0));
        t.setCapacity(8);
        return t;
    }

    private void given(MaterializeDTO.ExceptionDTO... extra) {
        when(repo.findRulesUpTo(eq(CENTER), any())).thenReturn(List.of(rule(closedDay, true), rule(substitute, false)));
        when(repo.findTemplateSlotsUpTo(eq(CENTER), any())).thenReturn(List.of(template(1, 13), template(2, 15)));

        MaterializeDTO.ExceptionDTO closed = new MaterializeDTO.ExceptionDTO();
        closed.setExceptionId(1);
        closed.setStartDate(closedDay);
        closed.setEndDate(closedDay);
        closed.setExceptionType("CLOSED");
        closed.setSubstituteDate(substitute);
        List<MaterializeDTO.ExceptionDTO> all = new ArrayList<>(List.of(closed));
        all.addAll(List.of(extra));
        when(repo.findExceptionsInRange(eq(CENTER), any(), any())).thenReturn(all);
    }

    @Test
    void 쉬는_요일인_대체일이_미운영일_회차로_열린다() {
        given();

        List<MaterializeDTO.InstanceDTO> slots = materializer.previewDay(CENTER, substitute);

        assertEquals(2, slots.size());
        assertEquals(substitute.atTime(13, 0), slots.get(0).getStartsAt());
        assertEquals(substitute.atTime(15, 0), slots.get(1).getStartsAt());
        assertEquals(8, slots.get(0).getCapacity());
    }

    @Test
    void 미운영일_자체는_닫힌다() {
        given();
        assertTrue(materializer.previewDay(CENTER, closedDay).isEmpty());
    }

    @Test
    void 대체일에_미운영_예외가_걸리면_열지_않는다() {
        MaterializeDTO.ExceptionDTO subClosed = new MaterializeDTO.ExceptionDTO();
        subClosed.setExceptionId(2);
        subClosed.setStartDate(substitute);
        subClosed.setEndDate(substitute);
        subClosed.setExceptionType("CLOSED");
        given(subClosed);

        assertTrue(materializer.previewDay(CENTER, substitute).isEmpty());
    }

    @Test
    void 일일_배치는_오픈일_너머_대체일도_찍는다() {
        when(repo.isWithSeodang(CENTER)).thenReturn(false);
        LocalDate until = materializer.openUntil(CENTER);
        LocalDate lastClosed = until;                 // 오픈 마지막 날 미운영 → 오픈일 너머 대체
        LocalDate lateSub = until.plusDays(1);

        MaterializeDTO.RuleDTO openRule = rule(lastClosed, true);
        MaterializeDTO.TemplateSlotDTO t = template(1, 13);
        t.setDayOfWeek(lastClosed.getDayOfWeek().getValue());
        t.setEffectiveFrom(openRule.getEffectiveFrom());
        when(repo.findRulesUpTo(eq(CENTER), any())).thenReturn(List.of(openRule));
        when(repo.findTemplateSlotsUpTo(eq(CENTER), any())).thenReturn(List.of(t));
        MaterializeDTO.ExceptionDTO closed = new MaterializeDTO.ExceptionDTO();
        closed.setExceptionId(1);
        closed.setStartDate(lastClosed);
        closed.setEndDate(lastClosed);
        closed.setExceptionType("CLOSED");
        closed.setSubstituteDate(lateSub);
        when(repo.findExceptionsInRange(eq(CENTER), any(), any())).thenReturn(List.of(closed));

        materializer.materializeHorizon(CENTER);

        verify(repo).mergeSlots(eq(CENTER), eq(lateSub), anyList());
    }
}
