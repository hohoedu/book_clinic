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

    /** item_del에 보관된 판본(bcode+center)을 item으로 복사 복원 (로그는 보존, 이미 존재하면 재복원 안 함) */
    void restoreItemFromDel(@Param("delId") Integer delId);

    /** 마스터 도서 검색 (제목/작가/장르/학년/키워드/유형/사용여부 복합 조건) */
    List<BookRespDTO.ContentRespDTO> searchContents(@Param("title") String title, @Param("author") String author, @Param("genre") String genre, @Param("schoolYear") String schoolYear, @Param("keyword") String keyword, @Param("contentType") String contentType, @Param("state") String state);

    /** 도서 제목으로 content_id 단건 조회 (엑셀 업로드 시 도서 선택 매핑용) */
    Integer findContentIdByTitle(@Param("title") String title);

    /** 학년별 도서 목록 엑셀 다운로드용 — 학년 코드명(codeNm) 순 정렬 */
    List<BookRespDTO.GradeListRespDTO> findContentsForGradeExcel();

    /** 바코드(ISBN)로 실물 도서 판본 대표 1건 조회 (동일 bcode 다른 센터로 복제 등록 시 원본 정보로도 사용) */
    BookRespDTO.ItemRespDTO findItemByBcode(@Param("bcode") String bcode);

    /** 실물 도서 검색 (마스터ID/출판사 복합 조건) */
    List<BookRespDTO.ItemRespDTO> searchItems(@Param("contentId") Integer contentId, @Param("state") String state, @Param("publisher") String publisher);

    /** 삭제된 마스터 도서 목록 조회 */
    List<BookRespDTO.ContentDelRespDTO> findDeletedContents();

    /** 삭제된 실물 도서 목록 조회 */
    List<BookRespDTO.ItemDelRespDTO> findDeletedItems();

    /** 특정 센터의 보유 도서(판본) 목록 조회 */
    List<BookRespDTO.ItemCenterRespDTO> findItemsByCenter(@Param("centerCode") String centerCode);

    /** 특정 (bcode + center) 보유수량 (행이 없으면 0) */
    int countItemsByBcodeCenter(@Param("bcode") String bcode, @Param("centerCode") String centerCode);

    /** (bcode + center) 행에 수량 deltaQty만큼 추가 — 행이 없으면 같은 bcode의 기존 판본 정보를 복사해 새로 만든다 */
    void cloneItemToCenter(@Param("bcode") String bcode, @Param("centerCode") String centerCode, @Param("deltaQty") int deltaQty);

    /** (bcode + center) 보유수량을 amount만큼 축소 — 가용 수량(qty-loaned_qty-lost_qty) 부족하면 아무 것도 안 함(영향받은 행 수로 판단) */
    int reduceItemQty(@Param("bcode") String bcode, @Param("centerCode") String centerCode, @Param("amount") int amount);

    /** 센터 도서(판본) 매핑 삭제 (bcode + centerCode 행 자체를 제거) */
    void deleteItemsByBcodeCenter(@Param("bcode") String bcode, @Param("centerCode") String centerCode);

    /** 해당 centerCode의 모든 판본 삭제 */
    void deleteItemsByCenterCode(@Param("centerCode") String centerCode);

    /**
     * (bcode + center)에서 가용 재고가 있으면 loaned_qty를 원자적으로 1 늘리고 그 item_id를 반환한다 (없으면 null)
     * — 관리자 화면의 수동 대여용
     */
    Integer loanAvailableItemByBcode(@Param("bcode") String bcode, @Param("centerCode") String centerCode, @Param("studentId") String studentId);

    /**
     * (content_id + center)에서 가용 재고가 있는 판본 1건의 loaned_qty를 원자적으로 1 늘리고 item_id를 반환한다 (없으면 null)
     * — 클리닉 자동 추천 확정용
     */
    Integer loanAvailableItemByContent(@Param("contentId") Integer contentId, @Param("centerCode") String centerCode, @Param("studentId") String studentId);

    /**
     * 이미 정해진 특정 item_id의 재고가 아직 있으면 loaned_qty를 원자적으로 1 늘린다(2026-07-30, item 기준 추천용).
     * ClinicService.pickWithFallback이 이미 재고 있는 item을 골라둔 뒤 이 메서드로 그 재고를 확정 예약한다 —
     * 고른 시점과 예약 시점 사이 다른 학생이 먼저 채갔을 수 있어 실패(0건 반영)할 수 있다.
     * @return 예약 성공한 item_id (실패하면 null)
     */
    Integer reserveItemById(@Param("itemId") Integer itemId);

    /**
     * 같은 책(content)의 대여 가능한 사본 하나를 원자적으로 예약한다 — 원래 판본(itemId) 우선,
     * 없으면 같은 센터의 다른 판본으로 대체. 문제풀이 기록을 되돌릴 때 그 사이 다른 학생이 그
     * 실물을 가져간 경우를 위한 경로다(BookService.secureCopyForStudent).
     * @return 예약된 item_id (원래 판본일 수도, 대체 사본일 수도 있다). 한 권도 없으면 null
     */
    Integer reserveCopyForContentOf(@Param("itemId") Integer itemId);

    /** 반납 처리 시 그 판본의 loaned_qty를 1 줄인다 */
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

    /** (content + center) 보유수량 총합 (여러 bcode 판본에 걸쳐 있을 수 있음) */
    int countItemsByContentCenter(@Param("contentId") Integer contentId, @Param("centerCode") String centerCode);

    /** 마스터 도서 행을 펼쳤을 때 보이는 하위 판본(bcode) 목록 (content + center) */
    List<BookRespDTO.StockItemRespDTO> findStockItems(@Param("contentId") Integer contentId,
            @Param("centerCode") String centerCode);

    /** (bcode + center) 대여중 수량 — bcode 단위 수량 축소 시 하한 판단용 */
    int countLoanedItemsByBcodeCenter(@Param("bcode") String bcode, @Param("centerCode") String centerCode);

    /** (content + center) 대여중 수량 총합 — 수량을 줄일 수 있는 하한 판단용 */
    int countLoanedItemsByContentCenter(@Param("contentId") Integer contentId, @Param("centerCode") String centerCode);

    /** 해당 도서의 대표 bcode 1건 (센터 무관) — 재고를 추가할 때 기존 바코드를 이어 쓰기 위해 */
    String findBcodeByContentId(@Param("contentId") Integer contentId);

    /** 마스터 도서 정보를 그대로 복사해 (bcode+center) 행에 수량 deltaQty만큼 추가 (없으면 새로 생성) */
    void insertItemFromContent(@Param("contentId") Integer contentId, @Param("centerCode") String centerCode,
            @Param("bcode") String bcode, @Param("deltaQty") int deltaQty);

    /** 보유 수량 변경 로그 기록 — memo는 감소 사유(늘릴 땐 null) */
    void insertStockLog(@Param("contentId") Integer contentId, @Param("centerCode") String centerCode,
            @Param("beforeQty") int beforeQty, @Param("afterQty") int afterQty,
            @Param("memo") String memo, @Param("changedBy") String changedBy);

    /** (content + center) 보유 수량 변경 이력 (최신순) */
    List<BookRespDTO.StockLogRespDTO> findStockLogs(@Param("contentId") Integer contentId,
            @Param("centerCode") String centerCode);
}
