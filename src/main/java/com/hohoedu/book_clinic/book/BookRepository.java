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

    /** 도서 분류별 상세(C=연계교과/R=추천기관/A=수상명) upsert - erp_bookstore_content_detail (content_id + gubun 복합키) */
    void upsertContentDetail(@Param("contentId") Integer contentId, @Param("gubun") String gubun, @Param("name") String name);

    /** 도서 분류별 상세 삭제 (특정 gubun 값 제거) */
    void deleteContentDetail(@Param("contentId") Integer contentId, @Param("gubun") String gubun);

    /** 마스터 도서 삭제 (저장 프로시저 sp_delete_book 호출) */
    void deleteBook(@Param("contentId") Integer contentId, @Param("deletedBy") String deletedBy);

    /** 마스터 도서 복구 (저장 프로시저 sp_restore_book 호출) */
    void restoreBook(@Param("delId") Integer delId);

    /** 실물 도서(사본) 1행 등록 — bcode를 공유하는 여러 사본을 등록할 땐 호출 측에서 반복 호출한다 */
    void registerItem(BookReqDTO.ItemRegisterReqDTO reqDTO);

    /** 실물 도서 수정 (같은 bcode를 공유하는 모든 사본에 반영) */
    void updateItem(BookReqDTO.ItemUpdateReqDTO reqDTO);

    /** 센터 보유분(bcode + center)의 삭제 로그를 item_del에 기록 (사본 행 자체는 deleteItemsByBcodeCenter로 별도 삭제) */
    void archiveItem(@Param("bcode") String bcode, @Param("centerCode") String centerCode, @Param("deletedBy") String deletedBy);

    /** 실물 도서 수정 전 스냅샷을 item_del에 UPDATE 로그로 기록 (bcode를 공유하는 모든 사본) */
    void archiveItemForUpdate(@Param("bcode") String bcode, @Param("updatedBy") String updatedBy);

    /** 마스터 도서 수정 전 스냅샷을 content_del에 UPDATE 로그로 기록 */
    void archiveContentForUpdate(@Param("contentId") Integer contentId, @Param("updatedBy") String updatedBy);

    /** 분류별 상세 수정 전 스냅샷을 content_detail_del에 UPDATE 로그로 기록 */
    void archiveContentDetailForUpdate(@Param("contentId") Integer contentId, @Param("updatedBy") String updatedBy);

    /** item_del에 보관된 사본(bcode+center)을 item으로 복사 복원 (로그는 보존, 이미 존재하면 재복원 안 함) */
    void restoreItemFromDel(@Param("delId") Integer delId);

    /** 마스터 도서 검색 (제목/작가/장르/학년/키워드/유형/사용여부 복합 조건) */
    List<BookRespDTO.ContentRespDTO> searchContents(@Param("title") String title, @Param("author") String author, @Param("genre") String genre, @Param("schoolYear") String schoolYear, @Param("keyword") String keyword, @Param("contentType") String contentType, @Param("state") String state);

    /** 도서 제목으로 content_id 단건 조회 (엑셀 업로드 시 도서 선택 매핑용) */
    Integer findContentIdByTitle(@Param("title") String title);

    /** 바코드(ISBN)로 실물 도서 사본 대표 1건 조회 (동일 bcode 다른 센터로 복제 등록 시 원본 정보로도 사용) */
    BookRespDTO.ItemRespDTO findItemByBcode(@Param("bcode") String bcode);

    /** 실물 도서 검색 (마스터ID/상태/출판사 복합 조건. state 파라미터는 이제 사본 status(AVAILABLE/LOANED/LOST) 값) */
    List<BookRespDTO.ItemRespDTO> searchItems(@Param("contentId") Integer contentId, @Param("state") String state, @Param("publisher") String publisher);

    /** 삭제된 마스터 도서 목록 조회 */
    List<BookRespDTO.ContentDelRespDTO> findDeletedContents();

    /** 삭제된 실물 도서 목록 조회 */
    List<BookRespDTO.ItemDelRespDTO> findDeletedItems();

    /** 특정 센터의 보유 도서(사본) 목록 조회 */
    List<BookRespDTO.ItemCenterRespDTO> findItemsByCenter(@Param("centerCode") String centerCode);

    /** 특정 (bcode + center) 보유 사본 수 */
    int countItemsByBcodeCenter(@Param("bcode") String bcode, @Param("centerCode") String centerCode);

    /** (bcode + center)에 새 사본 1건 복제 등록 (같은 bcode의 기존 사본 도서 정보를 그대로 복사) */
    void cloneItemToCenter(@Param("bcode") String bcode, @Param("centerCode") String centerCode);

    /** (bcode + center) 보유 사본 중 대여 중이 아닌(AVAILABLE) 사본을 최대 limit건 삭제 */
    void deleteAvailableItems(@Param("bcode") String bcode, @Param("centerCode") String centerCode, @Param("limit") int limit);

    /** 센터 도서(사본) 매핑 삭제 (bcode + centerCode 전체 사본) */
    void deleteItemsByBcodeCenter(@Param("bcode") String bcode, @Param("centerCode") String centerCode);

    /** 해당 centerCode의 모든 사본 삭제 */
    void deleteItemsByCenterCode(@Param("centerCode") String centerCode);

    /**
     * (bcode + center)에서 대여 가능(AVAILABLE)한 사본 1건을 원자적으로 대여 처리(status=LOANED, last_* 갱신)하고
     * 그 사본의 item_id를 반환한다 (없으면 null) — 관리자 화면의 수동 대여용
     */
    Integer loanAvailableItemByBcode(@Param("bcode") String bcode, @Param("centerCode") String centerCode, @Param("studentId") String studentId);

    /**
     * (content_id + center)에서 대여 가능(AVAILABLE)한 사본 1건을 원자적으로 대여 처리하고 item_id를 반환한다 (없으면 null)
     * — 클리닉 자동 추천 확정용
     */
    Integer loanAvailableItemByContent(@Param("contentId") Integer contentId, @Param("centerCode") String centerCode, @Param("studentId") String studentId);

    /** 반납 처리 시 사본 상태를 AVAILABLE로 되돌리고 last_returned_at 갱신 */
    void markItemReturned(@Param("itemId") Integer itemId);

    /** 대여 이력 등록 (사본의 item_id 기준) */
    void insertItemLoan(@Param("itemId") Integer itemId, @Param("studentId") String studentId);

    /** 대여 이력을 반납 처리(LOANED -> RETURNED) — 반영된 행 수로 처리 가능 여부 판단 */
    int updateLoanReturned(@Param("loanId") Integer loanId);

    /** 대여 이력 단건 조회 (반납 처리 전 상태/item_id 확인용, bcode/centerCode는 조인으로 채움) */
    BookRespDTO.ItemLoanRespDTO findLoanById(@Param("loanId") Integer loanId);

    /** 특정 실물도서(bcode+센터)의 대여 중(LOANED) 이력 목록 (학생명 포함) */
    List<BookRespDTO.ItemLoanRespDTO> findActiveLoansByItem(@Param("bcode") String bcode, @Param("centerCode") String centerCode);

    /** 학생의 현재 대여 중(LOANED) 이력 1건 (없으면 null) — 추천 갱신 시 이전 hold 반납 판단용 */
    BookRespDTO.ItemLoanRespDTO findActiveLoanByStudent(@Param("studentId") String studentId);

    // ===================== 보유도서 설정 (센터별 보유 수량) =====================

    /** 보유도서 설정 목록 — 마스터 도서 전체에 우리 센터 보유 수량을 붙여 조회 (보유 0권 도서도 포함) */
    List<BookRespDTO.StockRespDTO> searchCenterStocks(@Param("centerCode") String centerCode,
            @Param("schoolYear") String schoolYear, @Param("contentType") String contentType,
            @Param("genre") String genre, @Param("hasStock") String hasStock, @Param("title") String title);

    /** (content + center) 보유 사본 수 */
    int countItemsByContentCenter(@Param("contentId") Integer contentId, @Param("centerCode") String centerCode);

    /** (content + center) 보유 사본 중 대여 중(LOANED)인 수 — 수량을 줄일 수 있는 하한 판단용 */
    int countLoanedItemsByContentCenter(@Param("contentId") Integer contentId, @Param("centerCode") String centerCode);

    /** 해당 도서의 대표 bcode 1건 (센터 무관) — 사본을 추가할 때 기존 바코드를 이어 쓰기 위해 */
    String findBcodeByContentId(@Param("contentId") Integer contentId);

    /** 마스터 도서 정보를 그대로 복사해 사본 1건 신규 등록 (그 도서의 사본이 어느 센터에도 없을 때) */
    void insertItemFromContent(@Param("contentId") Integer contentId, @Param("centerCode") String centerCode,
            @Param("bcode") String bcode);

    /** (content + center) 사본 중 대여 중이 아닌(AVAILABLE) 1건 삭제 — 삭제된 행 수 반환 */
    int deleteAvailableItemByContent(@Param("contentId") Integer contentId, @Param("centerCode") String centerCode);

    /** 보유 수량 변경 로그 기록 */
    void insertStockLog(@Param("contentId") Integer contentId, @Param("centerCode") String centerCode,
            @Param("beforeQty") int beforeQty, @Param("afterQty") int afterQty,
            @Param("changedBy") String changedBy);

    /** (content + center) 보유 수량 변경 이력 (최신순) */
    List<BookRespDTO.StockLogRespDTO> findStockLogs(@Param("contentId") Integer contentId,
            @Param("centerCode") String centerCode);
}
