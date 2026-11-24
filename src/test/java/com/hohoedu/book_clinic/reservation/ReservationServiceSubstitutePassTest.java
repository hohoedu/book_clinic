package com.hohoedu.book_clinic.reservation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hohoedu.book_clinic._core.utils.KstClock;
import com.hohoedu.book_clinic.pass.PassService;
import com.hohoedu.book_clinic.reservation._dto.ReservationReqDTO;
import com.hohoedu.book_clinic.reservation._dto.ReservationRespDTO;
import com.hohoedu.book_clinic.schedule.materialize.ScheduleMaterializer;
import com.hohoedu.book_clinic.student.StudentRepository;
import com.hohoedu.book_clinic.student.model.Student;

/** 대체 운영일 예약은 원래 미운영일의 이용권·예약 오픈 기준을 쓴다 (2026-11-23) */
@ExtendWith(MockitoExtension.class)
class ReservationServiceSubstitutePassTest {

    private static final String STUDENT = "S1";
    private static final long SLOT = 20L, RES = 200L;

    @Mock ReservationRepository repository;
    @Mock StudentRepository studentRepository;
    @Mock PassService passService;
    @Mock ScheduleMaterializer scheduleMaterializer;
    @InjectMocks ReservationService service;

    /** 12/31 미운영 → 1/2 대체 같은 관계 */
    private final LocalDate closedDay = KstClock.today().plusDays(10);
    private final LocalDate substitute = closedDay.plusDays(2);

    @BeforeEach
    void setUp() {
        when(studentRepository.findById(STUDENT)).thenReturn(Student.builder().studentId(STUDENT).centerCode("C1").build());
        // 예약 오픈은 미운영일까지 — 대체일은 그 너머지만 열려 있어야 한다
        when(scheduleMaterializer.openUntil(anyString())).thenReturn(closedDay);
        when(scheduleMaterializer.closedDayOf("C1", substitute)).thenReturn(closedDay);

        ReservationRespDTO.SlotOptionDTO slot = new ReservationRespDTO.SlotOptionDTO();
        slot.setSlotInstanceId(SLOT);
        slot.setServiceDate(substitute);
        slot.setSeq(2);
        slot.setStartsAt(substitute.atTime(13, 0));
        slot.setEndsAt(substitute.atTime(14, 0));
        when(repository.findSlotOptionById(SLOT)).thenReturn(slot);
        when(repository.incrementReservedCount(SLOT)).thenReturn(1);
        org.mockito.Mockito.doAnswer(inv -> {
            inv.<ReservationReqDTO.InsertReservationDTO>getArgument(0).setReservationId(RES);
            return null;
        }).when(repository).insertReservation(any());
    }

    @Test
    void 미운영일_이용권으로_대체일을_예약한다() {
        when(passService.consumeForReservation(STUDENT, "BOOK", RES, substitute, closedDay)).thenReturn(true);

        service.reserve(STUDENT, SLOT);

        verify(passService, never()).consumeForReservation(any(), any(), any(), eq(substitute));
    }

    @Test
    void 미운영일_이용권이_없으면_대체일_이용권을_쓴다() {
        when(passService.consumeForReservation(STUDENT, "BOOK", RES, substitute, closedDay)).thenReturn(false);
        when(passService.consumeForReservation(STUDENT, "BOOK", RES, substitute)).thenReturn(true);

        service.reserve(STUDENT, SLOT);

        verify(passService).consumeForReservation(STUDENT, "BOOK", RES, substitute);
    }
}
