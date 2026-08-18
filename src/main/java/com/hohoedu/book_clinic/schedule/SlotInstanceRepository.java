package com.hohoedu.book_clinic.schedule;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hohoedu.book_clinic.schedule._dto.MaterializeDTO;

/**
 * 실체 슬롯(erp_bookstore_slot_instance) 매퍼 + materialize()의 입력 조회 (2026-08-18).
 *
 * 조회 메서드들이 하나같이 "날짜 하나"가 아니라 "기간"을 받는 이유는, 요일 버전을 저장하면
 * 오늘~예약 오픈일 사이의 같은 요일 전부(보통 4일)를 다시 찍어야 하기 때문이다. 날짜마다
 * 규칙·예외를 다시 조회하면 쿼리가 날짜 수만큼 늘어나므로 기간 전체를 한 번에 읽어 메모리에서 고른다.
 */
@Mapper
public interface SlotInstanceRepository {

    /** toDate까지 적용될 수 있는 모든 요일 버전 (요일, 적용시작일 오름차순) */
    List<MaterializeDTO.RuleDTO> findRulesUpTo(@Param("centerCode") String centerCode,
                                               @Param("toDate") LocalDate toDate);

    /** 위 버전들에 속한 회차 템플릿 전부 */
    List<MaterializeDTO.TemplateSlotDTO> findTemplateSlotsUpTo(@Param("centerCode") String centerCode,
                                                               @Param("toDate") LocalDate toDate);

    /** 기간과 하루라도 겹치는 예외 전부 */
    List<MaterializeDTO.ExceptionDTO> findExceptionsInRange(@Param("centerCode") String centerCode,
                                                            @Param("fromDate") LocalDate fromDate,
                                                            @Param("toDate") LocalDate toDate);

    /** 위 예외들에 달린 회차별 오버라이드 전부 */
    List<MaterializeDTO.ExceptionSlotDTO> findExceptionSlotsInRange(@Param("centerCode") String centerCode,
                                                                    @Param("fromDate") LocalDate fromDate,
                                                                    @Param("toDate") LocalDate toDate);

    /** 기간 중 유효 예약(RESERVED)이 하나라도 걸린 날짜들 — 이 날짜는 재생성 대상에서 제외한다 */
    List<LocalDate> findReservedDatesInRange(@Param("centerCode") String centerCode,
                                             @Param("fromDate") LocalDate fromDate,
                                             @Param("toDate") LocalDate toDate);

    /**
     * 이번에 찍을 회차 목록(keepSeqs)에 없는 잉여 슬롯 삭제. keepSeqs가 비면 그날 전부 삭제(휴무).
     * 예약 행이 하나라도 참조하는 슬롯은 남긴다 — 취소된 예약도 FK로 슬롯을 붙잡고 있어서
     * 그냥 지우면 참조 무결성 위반으로 터진다.
     */
    void deleteObsoleteSlots(@Param("centerCode") String centerCode,
                             @Param("serviceDate") LocalDate serviceDate,
                             @Param("keepSeqs") List<Integer> keepSeqs);

    /** 운영 규칙이 하나라도 등록된 센터 목록 — 일일 배치가 돌 대상 */
    List<String> findCenterCodesWithSchedule();

    /** 회차 목록을 (센터, 날짜, 회차) 기준으로 upsert — 기존 행은 slot_instance_id를 유지한 채 갱신 */
    void mergeSlots(@Param("centerCode") String centerCode,
                    @Param("serviceDate") LocalDate serviceDate,
                    @Param("slots") List<MaterializeDTO.InstanceDTO> slots);

}
