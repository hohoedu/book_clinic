package com.hohoedu.book_clinic.book;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.handler.exception.Exception404;
import com.hohoedu.book_clinic.book._dto.BookReqDTO;
import com.hohoedu.book_clinic.book._dto.BookRespDTO;
import com.hohoedu.book_clinic.student.StudentRepository;
import com.hohoedu.book_clinic.student.model.Student;

import lombok.RequiredArgsConstructor;

/**
 * 도서 비즈니스 로직 서비스
 * - 마스터 도서(content), 실물 도서(item, 사본 1행 = 실물 1권. 2026-07-13 재설계로 센터
 * 매핑(item_center) 통합) 처리
 * - 삭제/복구는 저장 프로시저(sp_delete_book, sp_restore_book) 호출
 */
@Service
@RequiredArgsConstructor
public class BookService {

    // 분류(content_type) 코드 → content_detail gubun (01=교과연계, 04=기관추천, 05=인증수상작)
    private static final Map<String, String> EXTRA_DETAIL_GUBUN_BY_CONTENT_TYPE = Map.of("01", "C", "04", "R", "05",
            "A");
    private static final List<String> ALL_DETAIL_GUBUNS = List.of("C", "R", "A");

    private final BookRepository bookRepository;
    private final StudentRepository studentRepository;

    /** 마스터 도서 등록 */
    @Transactional
    public void registerContent(BookReqDTO.RegisterReqDTO reqDTO) {
        bookRepository.registerContent(reqDTO);
        saveExtraDetail(reqDTO.getContentId(), reqDTO.getContentType(), reqDTO.getExtraDetail());
    }

    /**
     * 마스터 도서 수정 — 변경 전 스냅샷을 content_del/content_detail_del에 UPDATE 로그로 남긴다
     * contentType이 안 넘어오면(예: 상태 토글처럼 일부 필드만 바꾸는 부분 수정) 분류 관련 정보는 건드리지 않는다 —
     * 그렇지 않으면 saveExtraDetail이 "분류 없음"으로 해석해 기존 연계교과/추천기관/수상명 정보를 지워버린다
     */
    @Transactional
    public void updateContent(BookReqDTO.UpdateReqDTO reqDTO, String updatedBy) {
        bookRepository.archiveContentForUpdate(reqDTO.getContentId(), updatedBy);
        bookRepository.updateContent(reqDTO);
        if (reqDTO.getContentType() != null) {
            bookRepository.archiveContentDetailForUpdate(reqDTO.getContentId(), updatedBy);
            saveExtraDetail(reqDTO.getContentId(), reqDTO.getContentType(), reqDTO.getExtraDetail());
        }
    }

    /**
     * 분류별 부가 정보(연계교과/추천기관/수상명) 저장 — 도서당 최대 1행이므로
     * 현재 분류에 해당하는 gubun에만 값을 넣고, 나머지 gubun은 정리한다
     * (분류가 바뀌어 이전 부가정보가 남아있는 경우 대비)
     */
    private void saveExtraDetail(Integer contentId, String contentType, String detail) {
        // Map.of()는 불변 맵이라 null 키로 get()하면 NPE를 던지므로, contentType 미전달(부분 수정) 케이스를 방어
        String activeGubun = contentType == null ? null : EXTRA_DETAIL_GUBUN_BY_CONTENT_TYPE.get(contentType);
        for (String gubun : ALL_DETAIL_GUBUNS) {
            if (gubun.equals(activeGubun) && detail != null && !detail.isBlank()) {
                bookRepository.upsertContentDetail(contentId, gubun, detail);
            } else {
                bookRepository.deleteContentDetail(contentId, gubun);
            }
        }
    }

    /** 마스터 도서 삭제 - 저장 프로시저로 연결된 item, itempool까지 일괄 처리 */
    public void deleteBook(BookReqDTO.DeleteReqDTO reqDTO, String deletedBy) {
        bookRepository.deleteBook(reqDTO.getContentId(), deletedBy);
    }

    /** 마스터 도서 복구 - 저장 프로시저로 del 테이블에서 원본 테이블로 복사 (로그는 보존) */
    public void restoreBook(BookReqDTO.RestoreReqDTO reqDTO) {
        bookRepository.restoreBook(reqDTO.getDelId());
    }

    /**
     * 실물 도서(사본) 등록 — quantity(기본 1)만큼 같은 bcode를 공유하는 사본 행을 해당 센터에 등록한다
     * (2026-07-13 재설계: item_center 통합으로 센터 지정이 등록과 동시에 이루어진다)
     */
    @Transactional
    public void registerItem(BookReqDTO.ItemRegisterReqDTO reqDTO) {
        // ISBN을 쓰지 않으므로 bcode 미전달 시 숫자 UUID 자동 생성
        if (reqDTO.getBcode() == null || reqDTO.getBcode().isBlank()) {
            reqDTO.setBcode(generateNumericBcode());
        }
        int quantity = reqDTO.getQuantity() == null || reqDTO.getQuantity() < 1 ? 1 : reqDTO.getQuantity();
        for (int i = 0; i < quantity; i++) {
            bookRepository.registerItem(reqDTO);
        }
    }

    /** 실물 도서 수정 (제목, 출판사, 키워드) — 변경 전 스냅샷을 item_del에 UPDATE 로그로 남긴다 */
    @Transactional
    public void updateItem(BookReqDTO.ItemUpdateReqDTO reqDTO, String updatedBy) {
        bookRepository.archiveItemForUpdate(reqDTO.getBcode(), updatedBy);
        bookRepository.updateItem(reqDTO);
    }

    /**
     * 실물 도서(센터 보유) 삭제
     * 우리 센터 보유분(해당 bcode의 모든 사본)을 item_del로 이관한 뒤 실제로 삭제한다.
     * 다른 센터가 같은 bcode를 보유 중이면 그쪽 사본에는 영향 없음.
     */
    @Transactional
    public void deleteItem(BookReqDTO.ItemDeleteReqDTO reqDTO, String deletedBy) {
        bookRepository.archiveItem(reqDTO.getBcode(), reqDTO.getCenterCode(), deletedBy);
        bookRepository.deleteItemsByBcodeCenter(reqDTO.getBcode(), reqDTO.getCenterCode());
    }

    /**
     * 실물 도서 복구
     * item_del에 보관된 사본(bcode+center) 정보를 그대로 복사해 되살린다 (로그는 보존).
     */
    @Transactional
    public void restoreItem(BookReqDTO.ItemRestoreReqDTO reqDTO) {
        bookRepository.restoreItemFromDel(reqDTO.getDelId());
    }

    /** 마스터 도서 검색 (제목/작가/장르/학년/키워드/유형/사용여부 복합 조건) */
    public List<BookRespDTO.ContentRespDTO> searchContents(String title, String author, String genre, String schoolYear,
            String keyword, String contentType, String state) {
        return bookRepository.searchContents(title, author, genre, schoolYear, keyword, contentType, state);
    }

    /** 바코드(ISBN)로 실물 도서 단건 조회 — 없으면 404 */
    public BookRespDTO.ItemRespDTO findItemByBcode(String bcode) {
        BookRespDTO.ItemRespDTO item = bookRepository.findItemByBcode(bcode);
        if (item == null)
            throw new Exception404("해당 바코드의 실물 도서를 찾을 수 없습니다: " + bcode);
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

    /** 기존 바코드를 다른(또는 같은) 센터에 quantity(기본 1)만큼 사본 복제 등록 */
    @Transactional
    public void registerItemCenter(BookReqDTO.ItemCenterRegisterReqDTO reqDTO) {
        int quantity = reqDTO.getQuantity() == null || reqDTO.getQuantity() < 1 ? 1 : reqDTO.getQuantity();
        for (int i = 0; i < quantity; i++) {
            bookRepository.cloneItemToCenter(reqDTO.getBcode(), reqDTO.getCenterCode());
        }
    }

    /** 센터 보유 사본 수량을 목표치로 조정 — 현재보다 많으면 복제 추가, 적으면 대여 중이 아닌 사본부터 제거 */
    @Transactional
    public void updateItemCenter(BookReqDTO.ItemCenterUpdateReqDTO reqDTO) {
        int current = bookRepository.countItemsByBcodeCenter(reqDTO.getBcode(), reqDTO.getCenterCode());
        int target = reqDTO.getQuantity();
        if (target > current) {
            for (int i = current; i < target; i++) {
                bookRepository.cloneItemToCenter(reqDTO.getBcode(), reqDTO.getCenterCode());
            }
        } else if (target < current) {
            bookRepository.deleteAvailableItems(reqDTO.getBcode(), reqDTO.getCenterCode(), current - target);
        }
    }

    /** 센터 도서 매핑(해당 bcode의 모든 사본) 삭제 */
    @Transactional
    public void deleteItemCenter(String bcode, String centerCode) {
        bookRepository.deleteItemsByBcodeCenter(bcode, centerCode);
    }

    /**
     * 실물 도서 대여 처리
     * 대여 가능(status=AVAILABLE)한 사본 1건을 원자적으로 대여 처리하고, 그 item_id로 대여 이력을 남긴다.
     */
    @Transactional
    public void loanItem(BookReqDTO.ItemLoanReqDTO reqDTO) {
        Student student = studentRepository.findByAppId(reqDTO.getAppId());
        if (student == null)
            throw new Exception404("해당 앱 ID의 학생을 찾을 수 없습니다: " + reqDTO.getAppId());

        Integer itemId = bookRepository.loanAvailableItemByBcode(reqDTO.getBcode(), reqDTO.getCenterCode(),
                student.getStudentId());
        if (itemId == null)
            throw new Exception400("대여 가능한 재고가 없습니다.");

        bookRepository.insertItemLoan(itemId, student.getStudentId());
    }

    /** 실물 도서 반납 처리 — 대여 이력을 반납 처리하고 사본 상태를 AVAILABLE로 되돌린다 */
    @Transactional
    public void returnItem(BookReqDTO.ItemReturnReqDTO reqDTO) {
        BookRespDTO.ItemLoanRespDTO loan = bookRepository.findLoanById(reqDTO.getLoanId());
        if (loan == null)
            throw new Exception404("대여 이력을 찾을 수 없습니다: " + reqDTO.getLoanId());
        if (!"LOANED".equals(loan.getStatus()))
            throw new Exception400("이미 반납 처리된 대여 건입니다.");

        bookRepository.updateLoanReturned(reqDTO.getLoanId());
        bookRepository.markItemReturned(loan.getItemId());
    }

    /** 특정 실물도서(bcode+센터)의 대여 중 이력 목록 조회 */
    public List<BookRespDTO.ItemLoanRespDTO> findActiveLoans(String bcode, String centerCode) {
        return bookRepository.findActiveLoansByItem(bcode, centerCode);
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
