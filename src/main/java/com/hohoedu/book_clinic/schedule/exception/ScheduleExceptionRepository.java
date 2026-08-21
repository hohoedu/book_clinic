package com.hohoedu.book_clinic.schedule.exception;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hohoedu.book_clinic.schedule._dto.MaterializeDTO;
import com.hohoedu.book_clinic.schedule._dto.ScheduleReqDTO;
import com.hohoedu.book_clinic.schedule._dto.ScheduleRespDTO;

/** 예외 일정(erp_bookstore_schedule_exception / _exception_slot) 매퍼 인터페이스 (2026-08-18) */
@Mapper
public interface ScheduleExceptionRepository {

    /** 화면 목록용 — fromDate 이후에 끝나는 예외들 (시작일 오름차순) */
    List<ScheduleRespDTO.ExceptionDTO> findExceptions(@Param("centerCode") String centerCode,
                                                      @Param("fromDate") LocalDate fromDate);

    /** 위 목록에 달린 회차 조정 전부 — 요약 문구를 만들기 위해 한 번에 읽는다 */
    List<MaterializeDTO.ExceptionSlotDTO> findExceptionSlots(@Param("exceptionIds") List<Integer> exceptionIds);

    /**
     * 겹치는 기간형(CLOSED/TIME_CHANGE) 예외 — 있으면 등록을 거부한다.
     * 같은 날에 "휴무"와 "운영시간 변경"이 동시에 걸리면 어느 쪽을 따라야 할지 정의할 수 없다.
     */
    List<ScheduleRespDTO.ExceptionDTO> findOverlappingPeriods(@Param("centerCode") String centerCode,
                                                              @Param("startDate") LocalDate startDate,
                                                              @Param("endDate") LocalDate endDate);

    /** 같은 날짜에 이미 등록된 회차 변경(SLOT_CHANGE) — 하루에 하나만 허용 */
    ScheduleRespDTO.ExceptionDTO findSlotChangeOn(@Param("centerCode") String centerCode,
                                                  @Param("targetDate") LocalDate targetDate);

    /** 등록. exception_id가 IDENTITY라 INSERT 후 req.exceptionId에 채번 결과가 들어온다 */
    void insertException(@Param("centerCode") String centerCode,
                         @Param("req") ScheduleReqDTO.SaveExceptionReqDTO req,
                         @Param("userId") String userId);

    void insertExceptionSlots(@Param("exceptionId") Integer exceptionId,
                              @Param("slots") List<ScheduleReqDTO.ExceptionSlotReqDTO> slots);

    /** 삭제 대상 조회 — 다른 센터의 예외를 지우지 못하도록 센터까지 조건에 넣는다 */
    ScheduleRespDTO.ExceptionDTO findById(@Param("centerCode") String centerCode,
                                          @Param("exceptionId") Integer exceptionId);

    void archiveException(@Param("exceptionId") Integer exceptionId,
                          @Param("logType") String logType,
                          @Param("loggedBy") String loggedBy);

    void archiveExceptionSlots(@Param("exceptionId") Integer exceptionId,
                               @Param("logType") String logType,
                               @Param("loggedBy") String loggedBy);

    /** exception_slot은 FK ON DELETE CASCADE로 함께 삭제된다 */
    void deleteException(@Param("centerCode") String centerCode,
                         @Param("exceptionId") Integer exceptionId);

}
