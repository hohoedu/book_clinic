package com.hohoedu.book_clinic.reservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hohoedu.book_clinic._core.utils.KstClock;
import com.hohoedu.book_clinic.pass.PassService;
import com.hohoedu.book_clinic.pass._dto.PassRespDTO;
import com.hohoedu.book_clinic.reservation._dto.ReservationRespDTO;
import com.hohoedu.book_clinic.schedule._dto.MaterializeDTO;
import com.hohoedu.book_clinic.schedule.materialize.ScheduleMaterializer;
import com.hohoedu.book_clinic.student.StudentRepository;
import com.hohoedu.book_clinic.student.model.Student;

/** 반복 예약 미리보기가 미운영일을 대체 운영일로 옮겨 잡는지 (2026-11-23) */
@ExtendWith(MockitoExtension.class)
class ReservationServiceBatchSubstituteTest {

    private static final String STUDENT = "S1";
    private static final int SEQ = 2;

    @Mock ReservationRepository repository;
    @Mock StudentRepository studentRepository;
    @Mock PassService passService;
    @Mock ScheduleMaterializer scheduleMaterializer;
    @InjectMocks ReservationService service;

    /** 24시간 마감에 안 걸리게 충분히 먼 첫 주 */
    private final LocalDate week1 = KstClock.today().plusDays(10);
    private final LocalDate week2 = week1.plusWeeks(1);
    private final LocalDate substitute = week2.plusDays(1);

    @BeforeEach
    void setUp() {
        when(studentRepository.findById(STUDENT)).thenReturn(Student.builder().studentId(STUDENT).centerCode("C1").build());
        when(scheduleMaterializer.openUntil(anyString())).thenReturn(LocalDate.MAX);
        cycle = new PassRespDTO.CycleDTO();
        cycle.setCycleFrom(week1);
        cycle.setRemaining(10);
        when(passService.cycleOn(eq(STUDENT), eq("BOOK"), any())).thenReturn(cycle);
    }

    private PassRespDTO.CycleDTO cycle;

    private MaterializeDTO.ExceptionDTO closedEx(LocalDate closed, LocalDate sub) {
        MaterializeDTO.ExceptionDTO ex = new MaterializeDTO.ExceptionDTO();
        ex.setStartDate(closed);
        ex.setEndDate(closed);
        ex.setExceptionType("CLOSED");
        ex.setSubstituteDate(sub);
        ex.setReason("행사");
        return ex;
    }

    private ReservationRespDTO.SlotOptionDTO slot(long id, LocalDate date) {
        ReservationRespDTO.SlotOptionDTO s = new ReservationRespDTO.SlotOptionDTO();
        s.setSlotInstanceId(id);
        s.setServiceDate(date);
        s.setSeq(SEQ);
        s.setStatus("OPEN");
        s.setCapacity(10);
        s.setReservedCount(0);
        s.setStartsAt(date.atTime(13, 0));
        s.setEndsAt(date.atTime(14, 0));
        return s;
    }

    @Test
    void 미운영일은_대체_운영일의_같은_회차로_잡힌다() {
        when(scheduleMaterializer.substituteExceptions(eq("C1"), eq(week1), any())).thenReturn(Map.of(week2, closedEx(week2, substitute)));
        when(repository.findSlotsByDates(eq("C1"), anyList(), eq(SEQ), eq(STUDENT)))
                .thenReturn(List.of(slot(1, week1), slot(2, substitute), slot(3, week1.plusWeeks(2)), slot(4, week1.plusWeeks(3))));

        List<ReservationRespDTO.BatchPreviewItemDTO> items =
                service.previewBatch(STUDENT, week1.getDayOfWeek().getValue(), SEQ, week1);

        assertEquals(List.of(week1, substitute, week1.plusWeeks(2), week1.plusWeeks(3)),
                items.stream().map(ReservationRespDTO.BatchPreviewItemDTO::getServiceDate).toList());
        assertEquals("OPEN", items.get(1).getTargetStatus());
        assertEquals(2L, items.get(1).getSlotInstanceId());
        assertEquals(week2, items.get(1).getSubstituteFor());
        assertEquals("행사", items.get(1).getSubstituteReason());
        assertEquals(null, items.get(0).getSubstituteFor());
    }

    @Test
    void 대체일이_없는_미운영일은_준비중이_아니라_미운영이다() {
        when(scheduleMaterializer.substituteExceptions(any(), any(), any())).thenReturn(Map.of());
        when(repository.findSlotsByDates(eq("C1"), anyList(), eq(SEQ), eq(STUDENT)))
                .thenReturn(List.of(slot(1, week1), slot(3, week1.plusWeeks(2)), slot(4, week1.plusWeeks(3))));

        List<ReservationRespDTO.BatchPreviewItemDTO> items =
                service.previewBatch(STUDENT, week1.getDayOfWeek().getValue(), SEQ, week1);

        assertEquals(week2, items.get(1).getServiceDate());
        assertEquals("CLOSED", items.get(1).getTargetStatus());
    }

    @Test
    void 이용권_유효기간이_지난_주차는_빠진다() {
        cycle.setCycleUntil(week2);
        when(scheduleMaterializer.substituteExceptions(any(), any(), any())).thenReturn(Map.of());
        when(repository.findSlotsByDates(eq("C1"), anyList(), eq(SEQ), eq(STUDENT)))
                .thenReturn(List.of(slot(1, week1), slot(2, week2)));

        List<ReservationRespDTO.BatchPreviewItemDTO> items =
                service.previewBatch(STUDENT, week1.getDayOfWeek().getValue(), SEQ, week1);

        assertEquals(List.of(week1, week2),
                items.stream().map(ReservationRespDTO.BatchPreviewItemDTO::getServiceDate).toList());
    }

    @Test
    void 대체일이_유효기간을_넘어도_미운영일_이용권으로_잡힌다() {
        // week2(유효기간 마지막 날) 미운영 → 다음 날 대체. 오픈도 week2까지만이다
        cycle.setCycleUntil(week2);
        when(scheduleMaterializer.openUntil(anyString())).thenReturn(week2);
        when(scheduleMaterializer.substituteExceptions(eq("C1"), eq(week1), any())).thenReturn(Map.of(week2, closedEx(week2, substitute)));
        when(repository.findSlotsByDates(eq("C1"), anyList(), eq(SEQ), eq(STUDENT)))
                .thenReturn(List.of(slot(1, week1), slot(2, substitute)));

        List<ReservationRespDTO.BatchPreviewItemDTO> items =
                service.previewBatch(STUDENT, week1.getDayOfWeek().getValue(), SEQ, week1);

        assertEquals(List.of(week1, substitute),
                items.stream().map(ReservationRespDTO.BatchPreviewItemDTO::getServiceDate).toList());
        assertEquals("OPEN", items.get(1).getTargetStatus());
    }
}
