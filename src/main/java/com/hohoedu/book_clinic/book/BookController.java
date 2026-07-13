package com.hohoedu.book_clinic.book;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hohoedu.book_clinic._core.auth.CustomUserDetails;
import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic.book._dto.BookReqDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 도서 관련 API 컨트롤러
 * - 마스터 도서(content): 도서 원본 정보 관리
 * - 실물 도서(item): 사본 1행 = 실물 1권, center_code를 직접 보유 (2026-07-13: item_center 통합)
 */
@Slf4j
@RestController
@RequestMapping("/book")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    // ===================== 마스터 도서 관리 =====================

    /** 마스터 도서 등록 (등록 직후 이어서 문제를 저장할 수 있도록 생성된 contentId를 응답으로 반환) */
    @PostMapping("/register")
    public ResponseEntity<?> registerContent(@RequestBody @Valid BookReqDTO.RegisterReqDTO reqDTO) {
        bookService.registerContent(reqDTO);
        return ResponseEntity.ok(ApiUtils.success(reqDTO.getContentId()));
    }

    /** 마스터 도서 수정 (수정 전 스냅샷을 del 테이블에 UPDATE 로그로 기록) */
    @PostMapping("/update")
    public ResponseEntity<?> updateContent(@RequestBody @Valid BookReqDTO.UpdateReqDTO reqDTO,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        bookService.updateContent(reqDTO, userDetails.getUsername());
        return ResponseEntity.ok(ApiUtils.success("수정되었습니다."));
    }

    /** 마스터 도서 삭제 (연결된 실물도서, 문제까지 일괄 삭제 후 del 테이블 이관) */
    @PostMapping("/delete")
    public ResponseEntity<?> deleteBook(@RequestBody @Valid BookReqDTO.DeleteReqDTO reqDTO,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        bookService.deleteBook(reqDTO, userDetails.getUsername());
        return ResponseEntity.ok(ApiUtils.success("삭제되었습니다."));
    }

    /** 마스터 도서 복구 (del 테이블에서 원본 테이블로 복원) */
    @PostMapping("/restore")
    public ResponseEntity<?> restoreBook(@RequestBody @Valid BookReqDTO.RestoreReqDTO reqDTO) {
        bookService.restoreBook(reqDTO);
        return ResponseEntity.ok(ApiUtils.success("복구되었습니다."));
    }

    /** 마스터 도서 검색 (제목/작가/장르/학년/키워드/유형 복합 조건) */
    @GetMapping("/search")
    public ResponseEntity<?> searchContents(
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "author", required = false) String author,
            @RequestParam(value = "genre", required = false) String genre,
            @RequestParam(value = "schoolYear", required = false) String schoolYear,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "contentType", required = false) String contentType,
            @RequestParam(value = "state", required = false) String state) {
        return ResponseEntity.ok(ApiUtils
                .success(bookService.searchContents(title, author, genre, schoolYear, keyword, contentType, state)));
    }

    /** 삭제된 마스터 도서 목록 조회 (복구 화면용) */
    @GetMapping("/deleted")
    public ResponseEntity<?> findDeletedContents() {
        return ResponseEntity.ok(ApiUtils.success(bookService.findDeletedContents()));
    }

    // ===================== 실물 도서 관리 =====================

    /** 실물 도서 등록 (ISBN 최초 등록 시 센터 매핑도 동시 처리) */
    @PostMapping("/item/register")
    public ResponseEntity<?> registerItem(@RequestBody @Valid BookReqDTO.ItemRegisterReqDTO reqDTO) {
        bookService.registerItem(reqDTO);
        return ResponseEntity.ok(ApiUtils.success("등록되었습니다."));
    }

    /** 실물 도서 수정 (도서 제목, 출판사, 키워드 — 수정 전 스냅샷을 del 테이블에 UPDATE 로그로 기록) */
    @PostMapping("/item/update")
    public ResponseEntity<?> updateItem(@RequestBody @Valid BookReqDTO.ItemUpdateReqDTO reqDTO,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        bookService.updateItem(reqDTO, userDetails.getUsername());
        return ResponseEntity.ok(ApiUtils.success("수정되었습니다."));
    }

    /** 실물 도서(센터 보유) 삭제 — item_del로 이관 후 해당 센터 매핑만 해제 (실물 레코드 유지) */
    @PostMapping("/item/delete")
    public ResponseEntity<?> deleteItem(@RequestBody @Valid BookReqDTO.ItemDeleteReqDTO reqDTO, @AuthenticationPrincipal CustomUserDetails userDetails) {
        bookService.deleteItem(reqDTO, userDetails.getUsername());
        return ResponseEntity.ok(ApiUtils.success("삭제되었습니다."));
    }

    /** 실물 도서 복구 (del 테이블에서 원본 테이블로 복원) */
    @PostMapping("/item/restore")
    public ResponseEntity<?> restoreItem(@RequestBody @Valid BookReqDTO.ItemRestoreReqDTO reqDTO) {
        bookService.restoreItem(reqDTO);
        return ResponseEntity.ok(ApiUtils.success("복구되었습니다."));
    }

    /** 바코드(ISBN)로 실물 도서 단건 조회 */
    @GetMapping("/item/{bcode}")
    public ResponseEntity<?> findItemByBcode(@PathVariable("bcode") String bcode) {
        return ResponseEntity.ok(ApiUtils.success(bookService.findItemByBcode(bcode)));
    }

    /** 실물 도서 검색 (마스터ID/상태/출판사 복합 조건) */
    @GetMapping("/item/search")
    public ResponseEntity<?> searchItems(
            @RequestParam(value = "contentId", required = false) Integer contentId,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "publisher", required = false) String publisher) {
        return ResponseEntity.ok(ApiUtils.success(bookService.searchItems(contentId, state, publisher)));
    }

    /** 삭제된 실물 도서 목록 조회 (복구 화면용) */
    @GetMapping("/item/deleted")
    public ResponseEntity<?> findDeletedItems() {
        return ResponseEntity.ok(ApiUtils.success(bookService.findDeletedItems()));
    }

    // ===================== 센터 도서 관리 =====================

    /**
     * 센터 도서 매핑 등록
     * ISBN이 이미 등록된 경우, 다른 센터에서 동일 ISBN을 보유할 때 사용
     */
    @PostMapping("/item/center/register")
    public ResponseEntity<?> registerItemCenter(@RequestBody @Valid BookReqDTO.ItemCenterRegisterReqDTO reqDTO) {
        bookService.registerItemCenter(reqDTO);
        return ResponseEntity.ok(ApiUtils.success("등록되었습니다."));
    }

    /** 센터 도서 수량/상태 수정 */
    @PutMapping("/item/center/update")
    public ResponseEntity<?> updateItemCenter(@RequestBody @Valid BookReqDTO.ItemCenterUpdateReqDTO reqDTO) {
        bookService.updateItemCenter(reqDTO);
        return ResponseEntity.ok(ApiUtils.success("수정되었습니다."));
    }

    /** 센터 도서 매핑 삭제 — PathVariable로 전달 (DELETE with body는 클라이언트 호환성 문제) */
    @DeleteMapping("/item/center/{bcode}/{centerCode}")
    public ResponseEntity<?> deleteItemCenter(
            @PathVariable("bcode") String bcode,
            @PathVariable("centerCode") String centerCode) {
        bookService.deleteItemCenter(bcode, centerCode);
        return ResponseEntity.ok(ApiUtils.success("삭제되었습니다."));
    }

    /** 특정 센터의 보유 도서 목록 조회 */
    @GetMapping("/item/center/{centerCode}")
    public ResponseEntity<?> findItemsByCenter(@PathVariable("centerCode") String centerCode) {
        return ResponseEntity.ok(ApiUtils.success(bookService.findItemsByCenter(centerCode)));
    }

    // ===================== 대여 관리 =====================

    /** 실물 도서 대여 처리 (대여 가능한 실물도서 1권을 찾아 상태를 LOANED로 바꾸고 대여 이력 등록) */
    @PostMapping("/item/loan")
    public ResponseEntity<?> loanItem(@RequestBody @Valid BookReqDTO.ItemLoanReqDTO reqDTO) {
        bookService.loanItem(reqDTO);
        return ResponseEntity.ok(ApiUtils.success("대여 처리되었습니다."));
    }

    /** 실물 도서 반납 처리 (대여 이력 반납 처리 + 해당 실물도서 상태를 AVAILABLE로 되돌림) */
    @PostMapping("/item/loan/return")
    public ResponseEntity<?> returnItem(@RequestBody @Valid BookReqDTO.ItemReturnReqDTO reqDTO) {
        bookService.returnItem(reqDTO);
        return ResponseEntity.ok(ApiUtils.success("반납 처리되었습니다."));
    }

    /** 특정 실물도서(bcode+센터)의 대여 중 이력 조회 */
    @GetMapping("/item/loan/{bcode}/{centerCode}")
    public ResponseEntity<?> findActiveLoans(
            @PathVariable("bcode") String bcode,
            @PathVariable("centerCode") String centerCode) {
        return ResponseEntity.ok(ApiUtils.success(bookService.findActiveLoans(bcode, centerCode)));
    }
}
