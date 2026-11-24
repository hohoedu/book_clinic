package com.hohoedu.book_clinic.schedule.exception;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.utils.KstClock;
import com.hohoedu.book_clinic.schedule._dto.MaterializeDTO;
import com.hohoedu.book_clinic.schedule._dto.ScheduleReqDTO;
import com.hohoedu.book_clinic.schedule.materialize.ScheduleMaterializer;
import com.hohoedu.book_clinic.schedule.materialize.SlotInstanceRepository;

/** 미운영 예외의 대체 운영일(선택) 검증 (2026-11-23) */
@ExtendWith(MockitoExtension.class)
class ScheduleExceptionSubstituteTest {

    private static final String CENTER = "C1";
    private final LocalDate closedDay = KstClock.today().plusDays(5);
    private final LocalDate substitute = closedDay.plusDays(1);

    @Mock ScheduleExceptionRepository exceptionRepo;
    @Mock SlotInstanceRepository slotRepo;
    @Mock ScheduleMaterializer materializer;
    @InjectMocks ScheduleExceptionService service;

    @BeforeEach
    void setUp() {
        lenient().when(materializer.openUntil(CENTER)).thenReturn(closedDay.plusDays(30));
        lenient().when(materializer.materializeRange(any(), any(), any()))
                .thenReturn(new MaterializeDTO.ResultDTO(0, 0, List.of()));
    }

    private ScheduleReqDTO.SaveExceptionReqDTO closed(LocalDate start, LocalDate end, LocalDate sub) {
        ScheduleReqDTO.SaveExceptionReqDTO req = new ScheduleReqDTO.SaveExceptionReqDTO();
        req.setStartDate(start);
        req.setEndDate(end);
        req.setExceptionType("CLOSED");
        req.setReason("행사");
        req.setSubstituteDate(sub);
        return req;
    }

    private MaterializeDTO.InstanceDTO slot(String status) {
        MaterializeDTO.InstanceDTO s = new MaterializeDTO.InstanceDTO();
        s.setSeq(1);
        s.setStatus(status);
        return s;
    }

    @Test
    void 대체일_없이도_미운영을_등록한다() {
        service.create(CENTER, "admin", closed(closedDay, closedDay, null));
        verify(exceptionRepo).insertException(eq(CENTER), any(), eq("admin"));
        verify(materializer, never()).previewDay(any(), any());
    }

    @Test
    void 대체일이_원래_쉬는_날이어도_등록하고_그날을_다시_찍는다() {
        when(materializer.previewDay(CENTER, closedDay)).thenReturn(List.of(slot("OPEN")));
        ScheduleReqDTO.SaveExceptionReqDTO req = closed(closedDay, closedDay, substitute);

        service.create(CENTER, "admin", req);

        verify(exceptionRepo).insertException(eq(CENTER), eq(req), eq("admin"));
        verify(materializer).materializeRange(CENTER, substitute, substitute);
    }

    @Test
    void 원래_쉬는_날을_미운영으로_하면서_대체일을_고르면_거부한다() {
        when(materializer.previewDay(CENTER, closedDay)).thenReturn(List.of());
        assertThrows(Exception400.class,
                () -> service.create(CENTER, "admin", closed(closedDay, closedDay, substitute)));
        verify(exceptionRepo, never()).insertException(any(), any(), any());
    }

    @Test
    void 대체일이_미운영_기간이면_거부한다() {
        when(materializer.previewDay(CENTER, closedDay)).thenReturn(List.of(slot("OPEN")));
        MaterializeDTO.ExceptionDTO other = new MaterializeDTO.ExceptionDTO();
        other.setExceptionType("CLOSED");
        other.setStartDate(substitute);
        other.setEndDate(substitute.plusDays(2));
        when(slotRepo.findExceptionsInRange(CENTER, substitute, substitute)).thenReturn(List.of(other));

        assertThrows(Exception400.class,
                () -> service.create(CENTER, "admin", closed(closedDay, closedDay, substitute)));
    }

    @Test
    void 이미_다른_미운영일의_대체일이면_거부한다() {
        when(materializer.previewDay(CENTER, closedDay)).thenReturn(List.of(slot("OPEN")));
        MaterializeDTO.ExceptionDTO other = new MaterializeDTO.ExceptionDTO();
        other.setExceptionType("CLOSED");
        other.setStartDate(closedDay.minusDays(7));
        other.setEndDate(closedDay.minusDays(7));
        other.setSubstituteDate(substitute);
        when(slotRepo.findExceptionsInRange(CENTER, substitute, substitute)).thenReturn(List.of(other));

        assertThrows(Exception400.class,
                () -> service.create(CENTER, "admin", closed(closedDay, closedDay, substitute)));
    }

    @Test
    void 기간형_미운영에는_대체일을_지정할_수_없다() {
        assertThrows(Exception400.class,
                () -> service.create(CENTER, "admin", closed(closedDay, closedDay.plusDays(2), closedDay.plusDays(3))));
    }

    @Test
    void 미운영이_아니면_대체일을_버린다() {
        ScheduleReqDTO.SaveExceptionReqDTO req = closed(closedDay, closedDay, substitute);
        req.setExceptionType("TIME_CHANGE");
        req.setOpenTime(java.time.LocalTime.of(10, 0));
        req.setCloseTime(java.time.LocalTime.of(12, 0));

        service.create(CENTER, "admin", req);

        assertNull(req.getSubstituteDate());
    }
}
