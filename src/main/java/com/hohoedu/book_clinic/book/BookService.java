package com.hohoedu.book_clinic.book;

import java.util.ArrayList;
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
 * - 마스터 도서(content), 실물 도서(item, bcode+센터당 1행 + qty/loaned_qty 카운터. 2026-07-29
 * 재설계로 사본 1행=1권 모델을 대체) 처리
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
     * 실물 도서(판본) 등록 — quantity(기본 1)만큼 (bcode+센터) 행의 qty를 채운다
     * (2026-07-29 재설계: 사본 1행=1권 대신 bcode+센터당 1행 + qty 카운터로 관리)
     */
    @Transactional
    public void registerItem(BookReqDTO.ItemRegisterReqDTO reqDTO) {
        // ISBN을 쓰지 않으므로 bcode 미전달 시 숫자 UUID 자동 생성
        if (reqDTO.getBcode() == null || reqDTO.getBcode().isBlank()) {
            reqDTO.setBcode(generateNumericBcode());
        }
        int quantity = reqDTO.getQuantity() == null || reqDTO.getQuantity() < 1 ? 1 : reqDTO.getQuantity();
        reqDTO.setQuantity(quantity);
        bookRepository.registerItem(reqDTO);
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

    /** 기존 바코드를 다른(또는 같은) 센터에 quantity(기본 1)만큼 재고 추가 등록 */
    @Transactional
    public void registerItemCenter(BookReqDTO.ItemCenterRegisterReqDTO reqDTO) {
        int quantity = reqDTO.getQuantity() == null || reqDTO.getQuantity() < 1 ? 1 : reqDTO.getQuantity();
        bookRepository.cloneItemToCenter(reqDTO.getBcode(), reqDTO.getCenterCode(), quantity);
    }

    /** 센터 보유수량을 목표치로 조정 — 현재보다 많으면 채우고, 적으면 대여/분실 중이 아닌 만큼만 줄인다 */
    @Transactional
    public void updateItemCenter(BookReqDTO.ItemCenterUpdateReqDTO reqDTO) {
        int current = bookRepository.countItemsByBcodeCenter(reqDTO.getBcode(), reqDTO.getCenterCode());
        int target = reqDTO.getQuantity();
        if (target > current) {
            bookRepository.cloneItemToCenter(reqDTO.getBcode(), reqDTO.getCenterCode(), target - current);
        } else if (target < current) {
            bookRepository.reduceItemQty(reqDTO.getBcode(), reqDTO.getCenterCode(), current - target);
        }
    }

    /** 센터 도서(판본) 매핑 삭제 */
    @Transactional
    public void deleteItemCenter(String bcode, String centerCode) {
        bookRepository.deleteItemsByBcodeCenter(bcode, centerCode);
    }

    /**
     * 실물 도서 대여 처리
     * 가용 재고(qty-loaned_qty-lost_qty)가 있는 판본의 loaned_qty를 원자적으로 1 늘리고, 그 item_id로 대여 이력을 남긴다.
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

    /** 실물 도서 반납 처리 — 대여 이력을 반납 처리하고 그 판본의 loaned_qty를 1 줄인다 */
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

    // ===================== 보유도서 설정 (센터별 보유 수량) =====================

    /** 보유도서 설정 목록 — 우리 센터 기준 (보유 0권 도서도 함께 나온다) */
    public List<BookRespDTO.StockRespDTO> searchCenterStocks(String centerCode, String schoolYear, String contentType,
            String genre, String hasStock, String title) {
        return bookRepository.searchCenterStocks(centerCode, schoolYear, contentType, genre, hasStock, title);
    }

    /** 센터 보유 수량을 목표치로 변경 — 감소 사유가 필요 없는 호출부(트리, 사본 추가)용 오버로드 */
    @Transactional
    public int updateCenterStock(Integer contentId, String centerCode, int target, String changedBy) {
        return updateCenterStock(contentId, centerCode, target, changedBy, null);
    }

    /**
     * 센터 보유 수량을 목표치로 변경 — 화면에 저장 버튼이 없고 +/- 즉시 반영이라 호출 1회 = 확정 1회다.
     * 여러 bcode(판본)에 걸쳐 있을 수 있지만, 늘릴 땐 대표 bcode 하나에 채우고 줄일 땐 가용 재고가 있는
     * bcode부터 순서대로 줄인다(대여/분실 중인 수량은 건드리지 않는다).
     * 줄이는 경우 memo(감소 사유)가 반드시 있어야 한다 — 일괄 등록 화면에서 실수로 숫자를 낮췄을 때의 안전장치.
     * 변경 결과는 stock_log에 before/after/memo로 남겨 "최근 변경일 / 변경 이력" 표시의 근거가 된다.
     *
     * @return 실제로 반영된 보유 수량
     */
    @Transactional
    public int updateCenterStock(Integer contentId, String centerCode, int target, String changedBy, String memo) {
        if (target < 0)
            throw new Exception400("보유 수량은 0권보다 적을 수 없습니다.");

        int before = bookRepository.countItemsByContentCenter(contentId, centerCode);
        if (target == before)
            return before;

        if (target > before) {
            // 같은 도서의 사본은 센터가 달라도 bcode를 공유한다 — 첫 등록이면 새 bcode를 발급
            String bcode = bookRepository.findBcodeByContentId(contentId);
            if (bcode == null || bcode.isBlank())
                bcode = generateNumericBcode();
            bookRepository.insertItemFromContent(contentId, centerCode, bcode, target - before);
        } else {
            if (memo == null || memo.isBlank())
                throw new Exception400("수량을 줄이려면 사유를 입력해야 합니다.");
            int loaned = bookRepository.countLoanedItemsByContentCenter(contentId, centerCode);
            if (target < loaned)
                throw new Exception400("대여 중인 " + loaned + "권은 반납 전까지 줄일 수 없습니다.");
            int reduced = reduceContentQtyAcrossBcodes(contentId, centerCode, before - target);
            if (reduced < before - target)
                throw new Exception400("줄일 수 있는 보유분이 없습니다. 잠시 후 다시 시도해 주세요.");
        }

        int after = bookRepository.countItemsByContentCenter(contentId, centerCode);
        bookRepository.insertStockLog(contentId, centerCode, before, after, target < before ? memo : null, changedBy);
        return after;
    }

    /**
     * (content + center)에 걸친 여러 bcode 판본에서 가용 재고가 있는 것부터 순서대로 amount만큼 줄인다.
     * 한 bcode의 가용 재고만으로 부족하면 다음 bcode로 넘어간다.
     *
     * @return 실제로 줄어든 수량 (amount보다 적으면 가용 재고가 부족했다는 뜻)
     */
    private int reduceContentQtyAcrossBcodes(Integer contentId, String centerCode, int amount) {
        int remaining = amount;
        for (BookRespDTO.StockItemRespDTO row : bookRepository.findStockItems(contentId, centerCode)) {
            if (remaining <= 0)
                break;
            int available = row.getQty() - row.getLoanedQty() - row.getLostQty();
            if (available <= 0)
                continue;
            int take = Math.min(available, remaining);
            if (bookRepository.reduceItemQty(row.getBcode(), centerCode, take) > 0)
                remaining -= take;
        }
        return amount - remaining;
    }

    /** 보유 수량 변경 이력 조회 */
    public List<BookRespDTO.StockLogRespDTO> findStockLogs(Integer contentId, String centerCode) {
        return bookRepository.findStockLogs(contentId, centerCode);
    }

    /**
     * 보유수량 일괄 등록 — 도서별로 화면에서 확정한 단계를 순서대로 재생한다. 한 도서 안에서 한 단계가
     * 실패하면 그 뒤 단계는 시도하지 않고 멈춘다(이미 성공한 앞 단계는 그대로 반영된 채 유지).
     * 각 단계는 updateCenterStock을 그대로 호출하므로 before/after/memo가 단계별로 각각 stock_log에 남는다.
     * 같은 클래스 내부 호출이라 updateCenterStock의 @Transactional은 단계별로 새 트랜잭션을 열지 않지만
     * (스프링 프록시 self-invocation 한계), 각 단계가 하는 일이 MERGE 한 번 + 로그 insert 한 번뿐이라 감수할 만하다.
     */
    public List<BookRespDTO.StockBulkResultRespDTO> updateCenterStockBulk(List<BookReqDTO.StockBulkItemReqDTO> items,
            String centerCode, String changedBy) {
        List<BookRespDTO.StockBulkResultRespDTO> results = new ArrayList<>();
        for (BookReqDTO.StockBulkItemReqDTO item : items) {
            BookRespDTO.StockBulkResultRespDTO result = new BookRespDTO.StockBulkResultRespDTO();
            result.setContentId(item.getContentId());
            result.setSuccess(true);
            try {
                for (BookReqDTO.StockBulkStepReqDTO step : item.getSteps()) {
                    result.setQuantity(updateCenterStock(item.getContentId(), centerCode, step.getQuantity(), changedBy, step.getMemo()));
                }
            } catch (Exception400 e) {
                result.setSuccess(false);
                result.setMessage(e.getMessage());
            }
            results.add(result);
        }
        return results;
    }

    /** 마스터 도서 행을 펼쳤을 때 보이는 하위 사본(item) 목록 조회 */
    public List<BookRespDTO.StockItemRespDTO> findStockItems(Integer contentId, String centerCode) {
        return bookRepository.findStockItems(contentId, centerCode);
    }

    /**
     * 보유수량을 bcode(사본 묶음) 단위로 목표치까지 변경 — 같은 마스터 도서라도 등록된 판본(bcode)이 다르면
     * 수량을 따로 관리한다. 대여/반납(사본 status)과는 무관하게, 여기서도 대여 중인 사본만은 보존한다.
     * 변경 이력은 지금까지처럼 마스터 도서(content) 기준 총 보유수량으로 기록한다.
     *
     * @return 실제로 반영된 해당 bcode의 보유 수량
     */
    @Transactional
    public int updateStockItemQuantity(Integer contentId, String bcode, String centerCode, int target, String changedBy, String memo) {
        if (target < 0)
            throw new Exception400("보유 수량은 0권보다 적을 수 없습니다.");

        int bcodeBefore = bookRepository.countItemsByBcodeCenter(bcode, centerCode);
        if (target == bcodeBefore)
            return bcodeBefore;

        int contentBefore = bookRepository.countItemsByContentCenter(contentId, centerCode);

        if (target > bcodeBefore) {
            bookRepository.cloneItemToCenter(bcode, centerCode, target - bcodeBefore);
        } else {
            if (memo == null || memo.isBlank())
                throw new Exception400("수량을 줄이려면 사유를 입력해야 합니다.");
            int loaned = bookRepository.countLoanedItemsByBcodeCenter(bcode, centerCode);
            if (target < loaned)
                throw new Exception400("대여 중인 " + loaned + "권은 반납 전까지 줄일 수 없습니다.");
            if (bookRepository.reduceItemQty(bcode, centerCode, bcodeBefore - target) == 0)
                throw new Exception400("줄일 수 있는 보유분이 없습니다. 잠시 후 다시 시도해 주세요.");
        }

        int bcodeAfter = bookRepository.countItemsByBcodeCenter(bcode, centerCode);
        int contentAfter = bookRepository.countItemsByContentCenter(contentId, centerCode);
        bookRepository.insertStockLog(contentId, centerCode, contentBefore, contentAfter, target < bcodeBefore ? memo : null, changedBy);
        return bcodeAfter;
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
