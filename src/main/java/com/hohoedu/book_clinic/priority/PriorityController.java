package com.hohoedu.book_clinic.priority;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hohoedu.book_clinic._core.auth.CustomUserDetails;
import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic.priority._dto.PriorityReqDTO;

import lombok.RequiredArgsConstructor;

/**
 * 권장도서 순위 API 컨트롤러
 * - 연도+학년별로 순위 초안(draft)을 여러 건 저장하고, 그중 하나를 적용(활성) 상태로 선택
 */
@RestController
@RequestMapping("/priority")
@RequiredArgsConstructor
public class PriorityController {

    private final PriorityService priorityService;

    /** 순위 초안 저장 (드래그로 정한 순서 그대로 저장) */
    @PostMapping("/draft")
    public ResponseEntity<?> saveDraft(@RequestBody @Valid PriorityReqDTO.SaveDraftReqDTO reqDTO,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Integer draftId = priorityService.saveDraft(reqDTO, userDetails.getLoginUser().getUserName());
        return ResponseEntity.ok(ApiUtils.success(draftId));
    }

    /** 현재 적용 중인 초안 기준 순위 목록 조회 (권장도서 순위 화면 기본 조회) */
    @GetMapping
    public ResponseEntity<?> findRankedBooks(
            @RequestParam("year") String year,
            @RequestParam("schoolYear") String schoolYear) {
        return ResponseEntity.ok(ApiUtils.success(priorityService.findRankedBooks(year, schoolYear)));
    }

    /** 저장된 초안 목록 조회 (예약 목록 보기) */
    @GetMapping("/draft/list")
    public ResponseEntity<?> findDrafts(
            @RequestParam("year") String year,
            @RequestParam("schoolYear") String schoolYear) {
        return ResponseEntity.ok(ApiUtils.success(priorityService.findDrafts(year, schoolYear)));
    }

    /** 초안 적용(선택) */
    @PostMapping("/draft/activate")
    public ResponseEntity<?> activateDraft(@RequestBody @Valid PriorityReqDTO.ActivateDraftReqDTO reqDTO) {
        priorityService.activateDraft(reqDTO);
        return ResponseEntity.ok(ApiUtils.success("적용되었습니다."));
    }

    /** 특정 초안의 순위 미리보기 (예약 목록에서 펼쳐보기) */
    @GetMapping("/draft/{draftId}/books")
    public ResponseEntity<?> findBooksByDraftId(@PathVariable("draftId") Integer draftId) {
        return ResponseEntity.ok(ApiUtils.success(priorityService.findBooksByDraftId(draftId)));
    }

    /** 초안 삭제 (_del로 이관만 하고 복구는 지원하지 않음) */
    @PostMapping("/draft/delete")
    public ResponseEntity<?> deleteDraft(@RequestBody @Valid PriorityReqDTO.DeleteDraftReqDTO reqDTO,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        priorityService.deleteDraft(reqDTO.getDraftId(), userDetails.getLoginUser().getUserName());
        return ResponseEntity.ok(ApiUtils.success("삭제되었습니다."));
    }

    /** 권장도서 순위 엑셀 다운로드 — 학년(초1~중등) 7개 시트, 해당 연도에 적용 중인 순위 */
    @GetMapping("/excel")
    public ResponseEntity<byte[]> downloadExcel(@RequestParam("year") String year) {
        byte[] bytes = priorityService.buildExcelWorkbook(year);

        String filename = "권장도서 순위_" + year + ".xlsx";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .body(bytes);
    }

}
