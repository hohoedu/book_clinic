package com.hohoedu.book_clinic._core.file;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.utils.ApiUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 이미지 업로드 API 컨트롤러
 * 저장은 ImageStorageService에 위임한다 (가비아 이미지 호스팅 FTP, 미설정 시 로컬 폴백).
 */
@Slf4j
@RestController
@RequestMapping("/book")
@RequiredArgsConstructor
public class ImageUploadController {

    private final ImageStorageService imageStorageService;

    /**
     * 도서 표지 업로드 — 저장 후 접근 가능한 URL 반환.
     * 파일명이 content_id라(2026-10-02) 도서를 먼저 저장해 content_id가 있어야 올릴 수 있다.
     * centerCode는 지점 하위도서 표지일 때만 보낸다 — 마스터 표지({contentId}.png)를 덮어쓰지 않게 이름을 가른다.
     */
    @PostMapping("/image")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file,
                                         @RequestParam("contentId") Integer contentId,
                                         @RequestParam(value = "centerCode", required = false) String centerCode) {
        validateImage(file);
        validateCenterCode(centerCode);
        try {
            return ResponseEntity.ok(ApiUtils.success(Map.of("url", imageStorageService.store(file, contentId, centerCode))));
        } catch (IOException e) {
            log.error("표지 이미지 업로드 실패", e);
            throw new Exception400("이미지 업로드 중 오류가 발생했습니다.");
        }
    }

    /**
     * 수집 카드 이미지 업로드 (2026-09-02) — 표지와 저장 디렉터리가 다르다(FTP cards/).
     * 반환된 URL을 도서 저장 시 card_url로 함께 보내면 erp_bookstore_card_path에 기록된다.
     */
    @PostMapping("/card-image")
    public ResponseEntity<?> uploadCardImage(@RequestParam("file") MultipartFile file,
                                             @RequestParam("contentId") Integer contentId) {
        validateImage(file);
        try {
            return ResponseEntity.ok(ApiUtils.success(Map.of("url", imageStorageService.storeCard(file, contentId))));
        } catch (IOException e) {
            log.error("카드 이미지 업로드 실패", e);
            throw new Exception400("이미지 업로드 중 오류가 발생했습니다.");
        }
    }

    /**
     * 워크시트(출력용) 업로드 (2026-09-14) — 카드/표지와 저장 디렉터리가 다르다(FTP worksheets/).
     * 반환된 URL을 도서 저장 시 worksheetUrl로 함께 보내면 erp_bookstore_card_path에 기록된다.
     *
     * 이미지 외에 PDF도 받는다(2026-10-02) — 워크시트를 PDF로 만들어 쓰는 경우가 많아서다.
     * PDF는 변환 없이 원본 그대로 저장하고, 출력할 때 WorksheetPrintService가 PDF 한 장으로 묶는다.
     */
    @PostMapping("/worksheet-image")
    public ResponseEntity<?> uploadWorksheetImage(@RequestParam("file") MultipartFile file,
                                                  @RequestParam("contentId") Integer contentId) {
        validateWorksheet(file);
        try {
            return ResponseEntity.ok(ApiUtils.success(Map.of("url", imageStorageService.storeWorksheet(file, contentId))));
        } catch (IOException e) {
            log.error("워크시트 업로드 실패", e);
            throw new Exception400("워크시트 업로드 중 오류가 발생했습니다.");
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new Exception400("업로드할 이미지가 없습니다.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new Exception400("이미지 파일만 업로드할 수 있습니다.");
        }
    }

    /** 센터코드는 파일명에 그대로 들어가므로 영숫자만 허용한다(경로 조작 방지) */
    private void validateCenterCode(String centerCode) {
        if (centerCode != null && !centerCode.isBlank() && !centerCode.trim().matches("[A-Za-z0-9]+")) {
            throw new Exception400("잘못된 센터코드입니다.");
        }
    }

    /** 워크시트는 이미지와 PDF를 모두 받는다 (2026-10-02) */
    private void validateWorksheet(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new Exception400("업로드할 파일이 없습니다.");
        }
        if (imageStorageService.isPdf(file)) return;
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new Exception400("이미지 또는 PDF 파일만 업로드할 수 있습니다.");
        }
    }
}
