package com.hohoedu.book_clinic.question;

import java.io.IOException;
import java.util.List;

import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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

import jakarta.validation.Valid;
import com.hohoedu.book_clinic._core.auth.CustomUserDetails;
import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic.question._dto.QuestionReqDTO;
import com.hohoedu.book_clinic.question._dto.QuestionRespDTO;

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

    /** 문제 등록 */
    @PostMapping("/register")
    public ResponseEntity<?> registerQuestion(@RequestBody @Valid QuestionReqDTO.RegisterReqDTO reqDTO) {
        questionService.registerQuestion(reqDTO);
        return ResponseEntity.ok(ApiUtils.success("등록되었습니다."));
    }

    /** 문제 수정 (수정 전 스냅샷을 itempool_del에 UPDATE 로그로 기록) */
    @PostMapping("/update")
    public ResponseEntity<?> updateQuestion(@RequestBody @Valid QuestionReqDTO.UpdateReqDTO reqDTO,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        questionService.updateQuestion(reqDTO, userDetails.getUsername());
        return ResponseEntity.ok(ApiUtils.success("수정되었습니다."));
    }

    /** 문제 삭제 (itempool_del에 DELETE 로그 기록) */
    @PostMapping("/delete")
    public ResponseEntity<?> deleteQuestion(@RequestBody @Valid QuestionReqDTO.DeleteReqDTO reqDTO,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        questionService.deleteQuestion(reqDTO, userDetails.getUsername());
        return ResponseEntity.ok(ApiUtils.success("삭제되었습니다."));
    }

    /** 문제 복구 (itempool_del에서 itempool로 복원) */
    @PostMapping("/restore")
    public ResponseEntity<?> restoreQuestion(@RequestBody @Valid QuestionReqDTO.RestoreReqDTO reqDTO) {
        questionService.restoreQuestion(reqDTO);
        return ResponseEntity.ok(ApiUtils.success("복구되었습니다."));
    }

    /**
     * 도서별 문제 목록 조회
     * contentId 필수, qtype/state 선택 필터
     *
     * [정답 노출] 이 엔드포인트는 학생 문제풀이 화면과 관리자 도서 데이터 화면이 같이 쓴다.
     * 학생 화면 때문에 permitAll이라, 정답(ans)까지 그대로 내려주면 로그인 없이도 정답을 볼 수
     * 있었다(2026-08-20 발견). 문제 편집이 필요한 관리자에게만 ans를 채워 보내고, 그 외에는
     * 비운다 — 채점은 어차피 서버가 itempool.ans로 직접 하므로 학생 화면엔 필요 없고,
     * "틀린 문제 풀기"용 오답 목록은 /clinic/quiz/submit 응답의 wrongQnums가 대신한다.
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchQuestions(
            @RequestParam(value = "contentId", required = true) Integer contentId,
            @RequestParam(value = "qlevel", required = false) String qlevel,
            @RequestParam(value = "qtype", required = false) String qtype,
            @RequestParam(value = "state", required = false) String state,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<QuestionRespDTO.QuestionDTO> questions = questionService.searchQuestions(contentId, qlevel, qtype, state);
        if (userDetails == null) {
            questions.forEach(q -> q.setAns(null));
        }
        return ResponseEntity.ok(ApiUtils.success(questions));
    }

    /** 삭제된 문제 목록 조회 (복구 화면용) */
    @GetMapping("/deleted")
    public ResponseEntity<?> findDeletedQuestions(
            @RequestParam Integer contentId) {
        return ResponseEntity.ok(ApiUtils.success(questionService.findDeletedQuestions(contentId)));
    }

    /**
     * 문제 일괄 등록용 엑셀 양식 다운로드
     * QuestionService 내부에서 DB 도서 목록 조회 후 드롭다운 삽입
     */
    @GetMapping("/upload/template")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        try (Workbook wb = questionService.createUploadTemplate();
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            wb.write(out);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(
                    MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDisposition(ContentDisposition.attachment().filename("question_template.xlsx").build());
            return ResponseEntity.ok().headers(headers).body(out.toByteArray());
        }
    }

    /**
     * 엑셀 파일로 문제 일괄 등록
     * mode=check(기본): 중복 확인만, 중복 있으면 409 반환
     * mode=overwrite: 기존 비활성화 후 새 문제 삽입
     * mode=inactive: 새 문제를 비활성으로 삽입
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadQuestions(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "check") String mode,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IOException {
        String uploadedBy = userDetails != null ? userDetails.getUsername() : "SYSTEM";
        QuestionRespDTO.UploadResultDTO result = questionService.uploadQuestions(file, mode, uploadedBy);
        if (result.hasDuplicates()) {
            return ResponseEntity.status(409).body(ApiUtils.success(result));
        }
        return ResponseEntity.ok(ApiUtils.success(result));
    }

}
