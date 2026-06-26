package com.hohoedu.book_clinic.book;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hohoedu.book_clinic.book._dto.BookReqDTO;
import com.hohoedu.book_clinic.book._dto.BookRespDTO;

/**
 * 도서 MyBatis 매퍼 인터페이스
 * BookMapper.xml과 매핑되어 DB 쿼리를 실행
 */
@Mapper
public interface BookRepository {

    /** 마스터 도서 등록 */
    void registerContent(BookReqDTO.RegisterReqDTO reqDTO);

    /** 마스터 도서 수정 */
    void updateContent(BookReqDTO.UpdateReqDTO reqDTO);

    /** 마스터 도서 삭제 (저장 프로시저 sp_delete_book 호출) */
    void deleteBook(@Param("contentId") Integer contentId, @Param("deletedBy") String deletedBy);

    /** 마스터 도서 복구 (저장 프로시저 sp_restore_book 호출) */
    void restoreBook(@Param("delId") Integer delId);

    /** 실물 도서 등록 */
    void registerItem(BookReqDTO.ItemRegisterReqDTO reqDTO);

    /** 실물 도서 수정 */
    void updateItem(BookReqDTO.ItemUpdateReqDTO reqDTO);

    /** 실물 도서를 item_del 테이블로 이관 (삭제 전 보관) */
    void archiveItem(@Param("bcode") String bcode, @Param("deletedBy") String deletedBy);

    /** 실물 도서 삭제 */
    void deleteItem(@Param("bcode") String bcode);

    /** item_del에서 item으로 복원 */
    void restoreItemFromDel(@Param("delId") Integer delId);

    /** item_del 레코드 삭제 */
    void deleteItemDel(@Param("delId") Integer delId);

    /** 마스터 도서 검색 (제목/작가/장르/학년/키워드/유형 복합 조건) */
    List<BookRespDTO.ContentRespDTO> searchContents(@Param("title") String title, @Param("author") String author, @Param("genre") String genre, @Param("schoolYear") String schoolYear, @Param("keyword") String keyword, @Param("contentType") String contentType);

    /** 도서 제목으로 content_id 단건 조회 (엑셀 업로드 시 도서 선택 매핑용) */
    Integer findContentIdByTitle(@Param("title") String title);

    /** 바코드(ISBN)로 실물 도서 단건 조회 (센터 매핑 정보 포함) */
    BookRespDTO.ItemRespDTO findItemByBcode(@Param("bcode") String bcode);

    /** 실물 도서 검색 (마스터ID/상태/출판사 복합 조건) */
    List<BookRespDTO.ItemRespDTO> searchItems(@Param("contentId") Integer contentId, @Param("state") String state, @Param("publisher") String publisher);

    /** 삭제된 마스터 도서 목록 조회 */
    List<BookRespDTO.ContentDelRespDTO> findDeletedContents();

    /** 삭제된 실물 도서 목록 조회 */
    List<BookRespDTO.ItemDelRespDTO> findDeletedItems();

    /** 특정 센터의 보유 도서 목록 조회 */
    List<BookRespDTO.ItemCenterRespDTO> findItemsByCenter(@Param("centerCode") String centerCode);

    /** 센터 도서 매핑 등록 */
    void registerItemCenter(BookReqDTO.ItemCenterRegisterReqDTO reqDTO);

    /** 센터 도서 수량/상태 수정 */
    void updateItemCenter(BookReqDTO.ItemCenterUpdateReqDTO reqDTO);

    /** 센터 도서 매핑 삭제 (bcode + centerCode 기준) */
    void deleteItemCenter(@Param("bcode") String bcode, @Param("centerCode") String centerCode);

    /** 해당 bcode의 모든 센터 매핑 삭제 (실물 도서 삭제 시 FK 정리용) */
    void deleteItemCenterByBcode(@Param("bcode") String bcode);
}
