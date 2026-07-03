package com.hohoedu.book_clinic.book;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hohoedu.book_clinic._core.handler.exception.Exception404;
import com.hohoedu.book_clinic.book._dto.BookReqDTO;
import com.hohoedu.book_clinic.book._dto.BookRespDTO;

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
     * ISBN 최초 등록 시 item INSERT 후, centerCode가 있으면 item_center도 함께 INSERT
     */
    @Transactional
    public void registerItem(BookReqDTO.ItemRegisterReqDTO reqDTO) {
        // ISBN을 쓰지 않으므로 bcode 미전달 시 숫자 UUID 자동 생성
        if (reqDTO.getBcode() == null || reqDTO.getBcode().isBlank()) {
            reqDTO.setBcode(generateNumericBcode());
        }
        bookRepository.registerItem(reqDTO);
        if (reqDTO.getCenterCode() != null && !reqDTO.getCenterCode().isBlank()) {
            BookReqDTO.ItemCenterRegisterReqDTO centerDTO = new BookReqDTO.ItemCenterRegisterReqDTO();
            centerDTO.setBcode(reqDTO.getBcode());
            centerDTO.setCenterCode(reqDTO.getCenterCode());
            centerDTO.setQuantity(reqDTO.getQuantity());
            centerDTO.setState(reqDTO.getState());
            bookRepository.registerItemCenter(centerDTO);
        }
    }

    /** 실물 도서 수정 (제목, 출판사, 키워드) */
    public void updateItem(BookReqDTO.ItemUpdateReqDTO reqDTO) {
        bookRepository.updateItem(reqDTO);
    }

    /**
     * 실물 도서(센터 보유) 삭제
     * 우리 센터 보유분을 item_del로 이관한 뒤 해당 센터 매핑만 해제한다.
     * 실물도서(item) 레코드는 유지 → 다른 센터 보유/공유에 영향 없음.
     */
    @Transactional
    public void deleteItem(BookReqDTO.ItemDeleteReqDTO reqDTO, String deletedBy) {
        bookRepository.archiveItem(reqDTO.getBcode(), reqDTO.getCenterCode(), deletedBy);
        bookRepository.deleteItemCenterCode(reqDTO.getBcode(), reqDTO.getCenterCode());
    }

    /**
     * 실물 도서 복구
     * 실물 레코드는 유지되므로 item_del에 보관된 센터 매핑만 되살린다.
     */
    @Transactional
    public void restoreItem(BookReqDTO.ItemRestoreReqDTO reqDTO) {
        bookRepository.restoreItemCenterFromDel(reqDTO.getDelId());
        bookRepository.deleteItemDel(reqDTO.getDelId());
    }

    /** 마스터 도서 검색 (제목/작가/장르/학년/키워드/유형/사용여부 복합 조건) */
    public List<BookRespDTO.ContentRespDTO> searchContents(String title, String author, String genre, String schoolYear, String keyword, String contentType, String state) {
        return bookRepository.searchContents(title, author, genre, schoolYear, keyword, contentType, state);
    }

    /** 바코드(ISBN)로 실물 도서 단건 조회 — 없으면 404 */
    public BookRespDTO.ItemRespDTO findItemByBcode(String bcode) {
        BookRespDTO.ItemRespDTO item = bookRepository.findItemByBcode(bcode);
        if (item == null) throw new Exception404("해당 바코드의 실물 도서를 찾을 수 없습니다: " + bcode);
        return item;
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
    public void deleteItemCenter(String bcode, String centerCode) {
        bookRepository.deleteItemCenterCode (bcode, centerCode);
    }

    /**
     * 실물 도서 식별자(bcode) 생성 — ISBN을 사용하지 않으므로 숫자로만 이루어진 UUID를 발급한다.
     * (타임스탬프 13자리 + 랜덤 5자리 = 18자리 숫자, VARCHAR(50) PK에 저장)
     */
    private String generateNumericBcode() {
        long timestamp = System.currentTimeMillis();
        int random = (int) (Math.random() * 100000);
        return timestamp + String.format("%05d", random);
    }
}
