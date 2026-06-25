package com.hohoedu.book_clinic.bookstore.question;

import java.io.IOException;
import java.util.List;

import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.hohoedu.book_clinic._core.auth.CustomUserDetails;
import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic.bookstore.book.BookService;
import com.hohoedu.book_clinic.bookstore.book._dto.BookRespDTO;
import com.hohoedu.book_clinic.bookstore.question._dto.QuestionReqDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 문제 관련 API 컨트롤러
 * erp_bookstore_itempool 기반으로 도서별 문제 등록/수정/삭제/복구/조회 제공
 */
@Slf4j
@RestController
@RequestMapping("/question")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;
    private final BookService bookService;

    /** 문제 등록 */
    @PostMapping("/register")
    public ResponseEntity<?> registerQuestion(@RequestBody QuestionReqDTO.RegisterReqDTO reqDTO) {
        questionService.registerQuestion(reqDTO);
        return ResponseEntity.ok(ApiUtils.success("등록되었습니다."));
    }

    /** 문제 수정 */
    @PostMapping("/update")
    public ResponseEntity<?> updateQuestion(@RequestBody QuestionReqDTO.UpdateReqDTO reqDTO) {
        questionService.updateQuestion(reqDTO);
        return ResponseEntity.ok(ApiUtils.success("수정되었습니다."));
    }

    /** 문제 삭제 (itempool_del로 이관) */
    @PostMapping("/delete")
    public ResponseEntity<?> deleteQuestion(@RequestBody QuestionReqDTO.DeleteReqDTO reqDTO, @AuthenticationPrincipal CustomUserDetails userDetails) {
        questionService.deleteQuestion(reqDTO, userDetails.getUsername());
        return ResponseEntity.ok(ApiUtils.success("삭제되었습니다."));
    }

    /** 문제 복구 (itempool_del에서 itempool로 복원) */
    @PostMapping("/restore")
    public ResponseEntity<?> restoreQuestion(@RequestBody QuestionReqDTO.RestoreReqDTO reqDTO) {
        questionService.restoreQuestion(reqDTO);
        return ResponseEntity.ok(ApiUtils.success("복구되었습니다."));
    }

    /**
     * 도서별 문제 목록 조회
     * contentId 필수, qtype/state 선택 필터
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchQuestions(
            @RequestParam(value = "contentId") Integer contentId,
            @RequestParam(value = "qtype", required = false) String qtype,
            @RequestParam(value = "state", required = false) String state) {
        return ResponseEntity.ok(ApiUtils.success(questionService.searchQuestions(contentId, qtype, state)));
    }

    /** 삭제된 문제 목록 조회 (복구 화면용) */
    @GetMapping("/deleted")
    public ResponseEntity<?> findDeletedQuestions(
            @RequestParam(value = "contentId") Integer contentId) {
        return ResponseEntity.ok(ApiUtils.success(questionService.findDeletedQuestions(contentId)));
    }

    /**
     * 문제 일괄 등록용 엑셀 양식 다운로드
     * DB에서 도서 목록을 조회해 엑셀 B2 셀에 드롭다운으로 삽입
     */
    @GetMapping("/upload/template")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        List<BookRespDTO.ContentRespDTO> books = bookService.searchContents(null, null, null, null, null, null);
        try (Workbook wb = questionService.createUploadTemplate(books);
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            wb.write(out);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDisposition(ContentDisposition.attachment().filename("question_template.xlsx").build());
            return ResponseEntity.ok().headers(headers).body(out.toByteArray());
        }
    }

    /**
     * 엑셀 파일로 문제 일괄 등록
     * 도서는 엑셀 B2 셀 드롭다운에서 선택 — contentId 별도 전달 불필요
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadQuestions(@RequestParam(value = "file") MultipartFile file) {
        try {
            int count = questionService.uploadQuestions(file);
            return ResponseEntity.ok(ApiUtils.success(count + "개의 문제가 등록되었습니다."));
        } catch (Exception e) {
            log.error("엑셀 업로드 실패: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiUtils.error(e.getMessage(), HttpStatus.BAD_REQUEST));
        }
    }

}
