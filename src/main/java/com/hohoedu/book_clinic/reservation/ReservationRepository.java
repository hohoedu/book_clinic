package com.hohoedu.book_clinic.reservation;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hohoedu.book_clinic.reservation._dto.ReservationRespDTO;

/** 클리닉 예약(erp_bookstore_clinic_reservation) 등록/삭제/조회 MyBatis 매퍼 인터페이스 (2026-07-23) */
@Mapper
public interface ReservationRepository {

    /** 같은 (studentId, date, timeSlot) 예약이 이미 있는지 — 중복 등록 방지 */
    boolean existsReservation(@Param("studentId") String studentId, @Param("date") LocalDate date,
                               @Param("timeSlot") String timeSlot);

    /** 신규 예약 등록 */
    void insertReservation(@Param("studentId") String studentId, @Param("date") LocalDate date,
                            @Param("timeSlot") String timeSlot);

    /** 예약 삭제 */
    void deleteReservation(@Param("reservationId") Integer reservationId);

    /** 특정 날짜의 예약 목록 (타임슬롯 오름차순, 학생명순) */
    List<ReservationRespDTO.ReservationRowDTO> findByDate(@Param("date") LocalDate date);

}
