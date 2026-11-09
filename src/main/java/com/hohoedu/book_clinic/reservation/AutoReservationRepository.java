package com.hohoedu.book_clinic.reservation;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hohoedu.book_clinic.reservation._dto.ReservationRespDTO;

/**
 * 매주 자동 예약 패턴(erp_bookstore_auto_reservation)과 10시 일괄 안내 대기열(erp_bookstore_auto_notice) 매퍼 (2026-10-27).
 */
@Mapper
public interface AutoReservationRepository {

    // ── 패턴 ─────────────────────────────────────────────────────────────

    List<ReservationRespDTO.AutoPatternDTO> findByStudent(@Param("studentId") String studentId);

    ReservationRespDTO.AutoPatternDTO findById(@Param("autoId") int autoId);

    ReservationRespDTO.AutoPatternDTO findByKey(@Param("studentId") String studentId,
                                                @Param("dayOfWeek") int dayOfWeek,
                                                @Param("seq") int seq);

    /** 켜져 있는 패턴 전부 — 새벽 배치가 훑는다 */
    List<ReservationRespDTO.AutoPatternDTO> findAllEnabled();

    List<ReservationRespDTO.AutoPatternDTO> findEnabledByStudent(@Param("studentId") String studentId);

    void insertPattern(@Param("studentId") String studentId,
                       @Param("dayOfWeek") int dayOfWeek,
                       @Param("seq") int seq,
                       @Param("filledUntil") LocalDate filledUntil);

    /** 다시 켜기 — 꺼져 있던 사이의 날짜는 건너뛰고 filledUntil 다음 날부터 다시 연다 */
    int enable(@Param("autoId") int autoId, @Param("filledUntil") LocalDate filledUntil);

    int disable(@Param("autoId") int autoId);

    int updateFilledUntil(@Param("autoId") int autoId, @Param("filledUntil") LocalDate filledUntil);

    /** 이용권 없음 표시 — 이미 표시돼 있으면 0행(안내를 한 번만 보내는 판정에 쓴다) */
    int markNoPass(@Param("autoId") int autoId, @Param("today") LocalDate today);

    int clearNoPass(@Param("autoId") int autoId);

    // ── 안내 대기열 ──────────────────────────────────────────────────────

    void insertNotice(@Param("studentId") String studentId, @Param("message") String message);

    List<ReservationRespDTO.AutoNoticeDTO> findUnsentNotices();

    int markNoticesSent(@Param("noticeIds") List<Integer> noticeIds);
}
