package com.hohoedu.book_clinic.common.code;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hohoedu.book_clinic.common.code._dto.CodeReqDTO;
import com.hohoedu.book_clinic.common.code._dto.CodeRespDTO;

/**
 * 공통 코드 MyBatis 매퍼 인터페이스
 * CodeMapper.xml과 매핑되어 DB 쿼리를 실행
 */
@Mapper
public interface CodeRepository {

    /** 공통 코드 등록 */
    void registerCode(CodeReqDTO.RegisterReqDTO reqDTO);

    /** 공통 코드 수정 */
    void updateCode(CodeReqDTO.UpdateReqDTO reqDTO);

    /** 공통 코드 삭제 */
    void deleteCode(@Param("codeId") Integer codeId);

    /** 특정 그룹 코드 목록 조회 (정렬순서 오름차순) */
    List<CodeRespDTO.CodeDTO> findByGroupCode(@Param("groupCode") String groupCode);

    /** 전체 공통 코드 목록 조회 (그룹코드, 정렬순서 오름차순) */
    List<CodeRespDTO.CodeDTO> findAll();

    /** 도서 코드(erp_bookstore_code) gubun별 목록 조회 - C:분류, G:장르, S:학년, T:문제영역, L:레벨 */
    List<CodeRespDTO.BookstoreCodeDTO> findBookstoreCodesByGubun(@Param("gubun") String gubun);

}
