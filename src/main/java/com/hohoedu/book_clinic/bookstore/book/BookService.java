package com.hohoedu.book_clinic.bookstore.book;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hohoedu.book_clinic.bookstore.book._dto.BookReqDTO;
import com.hohoedu.book_clinic.bookstore.book._dto.BookRespDTO;

import lombok.RequiredArgsConstructor;

/**
 * 도서 비즈니스 로직 서비스
 * - 마스터 도서(content), 실물 도서(item), 센터 매핑(item_center) 처리
 * - 삭제/복구는 저장 프로시저(sp_delete_book, sp_restore_book) 호출
 */
@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRepository bookRepository;

    /** 마스터 도서 등록 */
    public void registerContent(BookReqDTO.RegisterReqDTO reqDTO) {
        bookRepository.registerContent(reqDTO);
    }

    /** 마스터 도서 수정 */
    public void updateContent(BookReqDTO.UpdateReqDTO reqDTO) {
        bookRepository.updateContent(reqDTO);
    }

    /** 마스터 도서 삭제 - 저장 프로시저로 연결된 item, itempool까지 일괄 처리 */
    public void deleteBook(BookReqDTO.DeleteReqDTO reqDTO, String deletedBy) {
        bookRepository.deleteBook(reqDTO.getContentId(), deletedBy);
    }

    /** 마스터 도서 복구 - 저장 프로시저로 del 테이블에서 원본 테이블로 복원 */
    public void restoreBook(BookReqDTO.RestoreReqDTO reqDTO) {
        bookRepository.restoreBook(reqDTO.getDelId());
    }

    /**
     * 실물 도서 등록
     * ISBN 최초 등록 시 item과 item_center를 동시에 INSERT
     */
    @Transactional
    public void registerItem(BookReqDTO.ItemRegisterReqDTO reqDTO) {
        bookRepository.registerItem(reqDTO);
        BookReqDTO.ItemCenterRegisterReqDTO centerDTO = new BookReqDTO.ItemCenterRegisterReqDTO();
        centerDTO.setBcode(reqDTO.getBcode());
        centerDTO.setCenterCode(reqDTO.getCenterCode());
        centerDTO.setQuantity(reqDTO.getQuantity());
        centerDTO.setState(reqDTO.getState());
        bookRepository.registerItemCenter(centerDTO);
    }

    /** 실물 도서 수정 (제목, 출판사, 키워드) */
    public void updateItem(BookReqDTO.ItemUpdateReqDTO reqDTO) {
        bookRepository.updateItem(reqDTO);
    }

    /**
     * 실물 도서 삭제
     * item_center FK 제약으로 인해 센터 매핑 먼저 삭제 후 item_del 이관
     */
    @Transactional
    public void deleteItem(BookReqDTO.ItemDeleteReqDTO reqDTO, String deletedBy) {
        bookRepository.deleteItemCenterByBcode(reqDTO.getBcode());
        bookRepository.archiveItem(reqDTO.getBcode(), deletedBy);
        bookRepository.deleteItem(reqDTO.getBcode());
    }

    /**
     * 실물 도서 복구
     * item_del에서 item으로 복원 후 del 레코드 제거
     */
    @Transactional
    public void restoreItem(BookReqDTO.ItemRestoreReqDTO reqDTO) {
        bookRepository.restoreItemFromDel(reqDTO.getDelId());
        bookRepository.deleteItemDel(reqDTO.getDelId());
    }

    /** 마스터 도서 검색 (제목/작가/장르/학년/키워드/유형 복합 조건) */
    public List<BookRespDTO.ContentRespDTO> searchContents(String title, String author, String genre, String schoolYear, String keyword, String contentType) {
        return bookRepository.searchContents(title, author, genre, schoolYear, keyword, contentType);
    }

    /** 바코드(ISBN)로 실물 도서 단건 조회 */
    public BookRespDTO.ItemRespDTO findItemByBcode(String bcode) {
        return bookRepository.findItemByBcode(bcode);
    }

    /** 실물 도서 검색 (마스터ID/상태/출판사 복합 조건) */
    public List<BookRespDTO.ItemRespDTO> searchItems(Integer contentId, String state, String publisher) {
        return bookRepository.searchItems(contentId, state, publisher);
    }

    /** 삭제된 마스터 도서 목록 조회 */
    public List<BookRespDTO.ContentDelRespDTO> findDeletedContents() {
        return bookRepository.findDeletedContents();
    }

    /** 삭제된 실물 도서 목록 조회 */
    public List<BookRespDTO.ItemDelRespDTO> findDeletedItems() {
        return bookRepository.findDeletedItems();
    }

    /** 특정 센터의 보유 도서 목록 조회 */
    public List<BookRespDTO.ItemCenterRespDTO> findItemsByCenter(String centerCode) {
        return bookRepository.findItemsByCenter(centerCode);
    }

    /** 기존 ISBN을 다른 센터에 추가 등록 */
    public void registerItemCenter(BookReqDTO.ItemCenterRegisterReqDTO reqDTO) {
        bookRepository.registerItemCenter(reqDTO);
    }

    /** 센터 도서 수량/상태 수정 */
    public void updateItemCenter(BookReqDTO.ItemCenterUpdateReqDTO reqDTO) {
        bookRepository.updateItemCenter(reqDTO);
    }

    /** 센터 도서 매핑 삭제 */
    public void deleteItemCenter(BookReqDTO.ItemCenterDeleteReqDTO reqDTO) {
        bookRepository.deleteItemCenter(reqDTO.getBcode(), reqDTO.getCenterCode());
    }
}
