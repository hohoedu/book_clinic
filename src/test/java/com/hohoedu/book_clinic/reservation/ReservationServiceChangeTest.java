package com.hohoedu.book_clinic.reservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.handler.exception.Exception404;
import com.hohoedu.book_clinic._core.utils.KstClock;
import com.hohoedu.book_clinic.pass.PassService;
import com.hohoedu.book_clinic.reservation._dto.ReservationReqDTO;
import com.hohoedu.book_clinic.reservation._dto.ReservationRespDTO;
import com.hohoedu.book_clinic.student.StudentRepository;
import com.hohoedu.book_clinic.student.model.Student;

/**
 * 예약 변경(2026-11-10) — 기존 예약 취소와 새 회차 예약이 한 번에 일어나는지,
 * 이용권은 복구 1회 + 차감 1회로 그대로인지, 같은 회차·남의 예약은 막는지 확인한다.
 * (롤백 자체는 @Transactional 몫이라 여기선 "실패가 예외로 올라가는지"까지만 본다)
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceChangeTest {

    private static final String STUDENT = "S1";
    private static final long OLD_RES = 100L, OLD_SLOT = 10L, NEW_SLOT = 20L, NEW_RES = 200L;

    @Mock ReservationRepository repository;
    @Mock StudentRepository studentRepository;
    @Mock PassService passService;
    @InjectMocks ReservationService service;

    /** 24시간 마감에 안 걸리게 충분히 먼 날짜 */
    private final LocalDateTime farStart = KstClock.now().plusDays(10).withHour(13).withMinute(0).withSecond(0).withNano(0);

    @BeforeEach
    void setUp() {
        when(studentRepository.findById(STUDENT)).thenReturn(Student.builder().studentId(STUDENT).centerCode("C1").build());
    }

    private ReservationRespDTO.ReservationItemDTO item(long id, String status) {
        ReservationRespDTO.ReservationItemDTO r = new ReservationRespDTO.ReservationItemDTO();
        r.setReservationId(id);
        r.setStatus(status);
        r.setStartsAt(farStart);
        return r;
    }

    private void givenNewSlotReservable() {
        ReservationRespDTO.SlotOptionDTO slot = new ReservationRespDTO.SlotOptionDTO();
        slot.setSlotInstanceId(NEW_SLOT);
        slot.setServiceDate(farStart.toLocalDate());
        slot.setSeq(2);
        slot.setStartsAt(farStart.plusHours(1));
        slot.setEndsAt(farStart.plusHours(2));
        when(repository.findSlotOptionById(NEW_SLOT)).thenReturn(slot);
        when(repository.incrementReservedCount(NEW_SLOT)).thenReturn(1);
        lenient().doAnswer(inv -> { // 마감 테스트에선 INSERT까지 안 간다
            inv.<ReservationReqDTO.InsertReservationDTO>getArgument(0).setReservationId(NEW_RES);
            return null;
        }).when(repository).insertReservation(any());
    }

    @Test
    void 예약된_건을_다른_회차로_옮기면_취소_복구_후_새로_예약_차감한다() {
        when(repository.findSlotInstanceIdByReservationId(OLD_RES, STUDENT)).thenReturn(OLD_SLOT);
        when(repository.findReservationById(OLD_RES)).thenReturn(item(OLD_RES, "RESERVED"));
        when(repository.cancelReservation(eq(OLD_RES), eq(STUDENT), anyString())).thenReturn(1);
        givenNewSlotReservable();
        when(passService.consumeForReservation(eq(STUDENT), eq("BOOK"), eq(NEW_RES), any(LocalDate.class))).thenReturn(true);
        when(repository.findReservationById(NEW_RES)).thenReturn(item(NEW_RES, "RESERVED"));

        ReservationRespDTO.ReservationItemDTO result = service.change(STUDENT, OLD_RES, NEW_SLOT);

        assertEquals(NEW_RES, result.getReservationId());
        verify(repository).decrementReservedCount(OLD_SLOT);
        verify(passService).restoreForReservation(OLD_RES);
        verify(passService).consumeForReservation(eq(STUDENT), eq("BOOK"), eq(NEW_RES), any(LocalDate.class));
    }

    @Test
    void 새_회차가_마감이면_예외로_올라가_전체가_롤백된다() {
        when(repository.findSlotInstanceIdByReservationId(OLD_RES, STUDENT)).thenReturn(OLD_SLOT);
        when(repository.findReservationById(OLD_RES)).thenReturn(item(OLD_RES, "RESERVED"));
        when(repository.cancelReservation(eq(OLD_RES), eq(STUDENT), anyString())).thenReturn(1);
        givenNewSlotReservable();
        when(repository.incrementReservedCount(NEW_SLOT)).thenReturn(0);

        assertThrows(Exception400.class, () -> service.change(STUDENT, OLD_RES, NEW_SLOT));
        verify(repository, never()).insertReservation(any());
    }

    @Test
    void 같은_회차로는_변경할_수_없다() {
        when(repository.findSlotInstanceIdByReservationId(OLD_RES, STUDENT)).thenReturn(OLD_SLOT);

        assertThrows(Exception400.class, () -> service.change(STUDENT, OLD_RES, OLD_SLOT));
        verify(repository, never()).cancelReservation(anyLong(), anyString(), anyString());
    }

    @Test
    void 내_예약이_아니면_찾을_수_없다() {
        when(repository.findSlotInstanceIdByReservationId(OLD_RES, STUDENT)).thenReturn(null);

        assertThrows(Exception404.class, () -> service.change(STUDENT, OLD_RES, NEW_SLOT));
        verify(repository, never()).cancelReservation(anyLong(), anyString(), anyString());
    }
}
