package com.hohoedu.book_clinic.schedule;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hohoedu.book_clinic.schedule._dto.ScheduleReqDTO;
import com.hohoedu.book_clinic.schedule._dto.ScheduleRespDTO;

/** 운영 스케줄 요일 규칙(erp_bookstore_schedule / _schedule_slot) 매퍼 인터페이스 (2026-08-14) */
@Mapper
public interface ScheduleRepository {

    /** 요일별로 baseDate에 유효한 버전(effective_from <= baseDate 중 최대) 1건씩 */
    List<ScheduleRespDTO.DayDTO> findEffectiveDays(@Param("centerCode") String centerCode,
                                                    @Param("baseDate") LocalDate baseDate);

    /** 위 유효 버전들에 속한 회차 템플릿 전부 (요일·회차 순) */
    List<ScheduleRespDTO.SlotDTO> findEffectiveSlots(@Param("centerCode") String centerCode,
                                                      @Param("baseDate") LocalDate baseDate);

    /** 센터의 모든 요일 버전 (적용시작일 오름차순) — 버전 목록 계산의 원본 */
    List<ScheduleRespDTO.VersionDayDTO> findAllVersionDays(@Param("centerCode") String centerCode);

    /** 특정 적용시작일 버전에 실제로 포함된 요일들 — 삭제 대상 판별용 */
    List<Integer> findVersionDayOfWeeks(@Param("centerCode") String centerCode,
                                        @Param("effectiveFrom") LocalDate effectiveFrom);

    /** 같은 (센터, 요일, 적용시작일) 버전이 이미 있는지 — 있으면 덮어쓰기(스냅샷 후 재생성) */
    boolean existsVersion(@Param("centerCode") String centerCode,
                          @Param("dayOfWeek") int dayOfWeek,
                          @Param("effectiveFrom") LocalDate effectiveFrom);

    /**
     * effectiveFrom 이후 날짜 중 이 요일에 걸린 유효 예약(RESERVED) 수.
     * 0보다 크면 저장을 거부한다 — 이미 예약한 학생의 회차가 말없이 바뀌면 안 되기 때문.
     */
    int countAffectedReservations(@Param("centerCode") String centerCode,
                                  @Param("dayOfWeek") int dayOfWeek,
                                  @Param("fromDate") LocalDate fromDate);

    /** 덮어쓰기 전 원본을 _del 테이블에 스냅샷 (log_type = UPDATE) */
    void archiveSchedule(@Param("centerCode") String centerCode,
                         @Param("dayOfWeek") int dayOfWeek,
                         @Param("effectiveFrom") LocalDate effectiveFrom,
                         @Param("logType") String logType,
                         @Param("loggedBy") String loggedBy);

    void archiveScheduleSlots(@Param("centerCode") String centerCode,
                              @Param("dayOfWeek") int dayOfWeek,
                              @Param("effectiveFrom") LocalDate effectiveFrom,
                              @Param("logType") String logType,
                              @Param("loggedBy") String loggedBy);

    /** 버전 삭제 — FK ON DELETE CASCADE로 schedule_slot도 함께 지워진다 */
    void deleteVersion(@Param("centerCode") String centerCode,
                       @Param("dayOfWeek") int dayOfWeek,
                       @Param("effectiveFrom") LocalDate effectiveFrom);

    void insertSchedule(@Param("centerCode") String centerCode,
                        @Param("effectiveFrom") LocalDate effectiveFrom,
                        @Param("day") ScheduleReqDTO.DayReqDTO day,
                        @Param("userId") String userId);

    void insertSlots(@Param("centerCode") String centerCode,
                     @Param("dayOfWeek") int dayOfWeek,
                     @Param("effectiveFrom") LocalDate effectiveFrom,
                     @Param("slots") List<ScheduleReqDTO.SlotReqDTO> slots);

}
