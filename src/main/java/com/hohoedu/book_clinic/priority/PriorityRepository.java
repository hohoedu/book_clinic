package com.hohoedu.book_clinic.priority;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hohoedu.book_clinic.priority._dto.PriorityReqDTO;
import com.hohoedu.book_clinic.priority._dto.PriorityRespDTO;

/**
 * 권장도서 순위 MyBatis 매퍼 인터페이스
 * PriorityMapper.xml과 매핑되어 DB 쿼리를 실행
 */
@Mapper
public interface PriorityRepository {

    /** 해당 연도+학년의 저장된 초안 건수 (최초 저장이면 자동 활성화하기 위해 사용) */
    int countDrafts(@Param("year") String year, @Param("schoolYear") String schoolYear);

    /** 초안 헤더 등록 (draftId는 useGeneratedKeys로 reqDTO에 채워짐) */
    void insertDraft(@Param("req") PriorityReqDTO.SaveDraftReqDTO reqDTO, @Param("isActive") String isActive, @Param("createdBy") String createdBy);

    /** 초안의 순위 내용(도서별 순번) 일괄 등록 */
    void insertPriorityRows(@Param("draftId") Integer draftId, @Param("items") List<PriorityReqDTO.RankedItem> items);

    /** 현재 적용 중인(is_active='Y') 초안 기준으로 학년 전체 도서 + 순위 조회 (순위 없는 도서는 뒤로) */
    List<PriorityRespDTO.RankedBookDTO> findRankedBooks(@Param("year") String year, @Param("schoolYear") String schoolYear);

    /** 해당 연도+학년의 저장된 초안 목록 (예약 목록 보기) */
    List<PriorityRespDTO.DraftDTO> findDrafts(@Param("year") String year, @Param("schoolYear") String schoolYear);

    /** 특정 초안에 저장된 순위 미리보기 (예약 목록에서 펼쳐보기) */
    List<PriorityRespDTO.RankedBookDTO> findBooksByDraftId(@Param("draftId") Integer draftId);

    /** 초안 단건 조회 (활성화 처리 시 연도+학년 범위 확인용) */
    PriorityRespDTO.DraftDTO findDraftById(@Param("draftId") Integer draftId);

    /** 해당 연도+학년의 모든 초안을 비활성화 */
    void deactivateDrafts(@Param("year") String year, @Param("schoolYear") String schoolYear);

    /** 특정 초안만 활성화 */
    void activateDraftById(@Param("draftId") Integer draftId);

    /** 삭제 이력 이관 - 초안 헤더 (복구 없음) */
    void insertDraftDel(@Param("draftId") Integer draftId, @Param("deletedBy") String deletedBy);

    /** 삭제 이력 이관 - 순위 내용 (복구 없음) */
    void insertPriorityDelRows(@Param("draftId") Integer draftId);

    /** 원본 순위 내용 삭제 */
    void deletePriorityRowsByDraftId(@Param("draftId") Integer draftId);

    /** 원본 초안 헤더 삭제 */
    void deleteDraftById(@Param("draftId") Integer draftId);

    /** 엑셀 다운로드용 — 해당 연도에 적용 중인 전체 학년 순위 조회 */
    List<PriorityRespDTO.ExcelRowDTO> findExcelRows(@Param("year") String year);

}
