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

    /** 도서 표지 업로드 — 저장 후 접근 가능한 URL 반환 */
    @PostMapping("/image")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
        validateImage(file);
        try {
            return ResponseEntity.ok(ApiUtils.success(Map.of("url", imageStorageService.store(file))));
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
    public ResponseEntity<?> uploadCardImage(@RequestParam("file") MultipartFile file) {
        validateImage(file);
        try {
            return ResponseEntity.ok(ApiUtils.success(Map.of("url", imageStorageService.storeCard(file))));
        } catch (IOException e) {
            log.error("카드 이미지 업로드 실패", e);
            throw new Exception400("이미지 업로드 중 오류가 발생했습니다.");
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
}
