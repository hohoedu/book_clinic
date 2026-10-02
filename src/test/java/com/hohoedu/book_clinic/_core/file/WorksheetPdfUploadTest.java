package com.hohoedu.book_clinic._core.file;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;

/**
 * 워크시트 업로드 — 2026-10-02.
 *
 * PDF는 손대지 않고 원본 그대로 보관한다. 한때 업로드 시점에 200dpi PNG로 구워 저장했는데,
 * 래스터로 굳히는 순간 인쇄물의 작은 글자가 뭉개졌다. 그 회귀를 막는 것이 이 테스트의 핵심이라,
 * 저장된 바이트가 올린 파일과 1바이트도 다르지 않은지까지 본다.
 *
 * 로컬 폴백이 없어졌으므로(2026-10-02) FTP 전송만 가로채 메모리에 담아 검증한다.
 */
class WorksheetPdfUploadTest {

    private static final int CONTENT_ID = 11;   // 파일명은 011.pdf

    /** URL → 업로드된 바이트 */
    private final Map<String, byte[]> uploaded = new HashMap<>();

    private ImageStorageService service() {
        ImageStorageService service = new ImageStorageService() {
            @Override
            String storeToGabia(InputStream in, String filename, String remoteDir) throws IOException {
                String url = "https://img.test/" + remoteDir + "/" + filename;
                uploaded.put(url, in.readAllBytes());
                return url;   // 실제 반환값엔 store()가 ?v=를 덧붙인다
            }
        };
        ReflectionTestUtils.setField(service, "ftpServer", "img.test");
        ReflectionTestUtils.setField(service, "worksheetDir", "bookstore/worksheets");
        return service;
    }

    /** A4 세로 빈 페이지 n장짜리 PDF */
    private byte[] pdf(int pages) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                document.addPage(new PDPage(PDRectangle.A4));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] png() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private MockMultipartFile upload(String filename, String contentType, byte[] bytes) {
        return new MockMultipartFile("file", filename, contentType, bytes);
    }

    /** 워크시트 디렉터리로 올라간 바이트 */
    private byte[] stored(String url) {
        assertTrue(url.startsWith("https://img.test/bookstore/worksheets/" + String.format("%03d", CONTENT_ID) + "."),
                "워크시트 디렉터리의 content_id 파일명이 아니다: " + url);
        assertTrue(url.contains("?v="), "캐시 무력화용 ?v=가 없다: " + url);
        return uploaded.get(url.substring(0, url.indexOf('?')));
    }

    @Test
    @DisplayName("PDF는 변환 없이 원본 그대로 저장된다")
    void pdf는_원본그대로_저장된다() throws IOException {
        ImageStorageService service = service();
        byte[] original = pdf(1);

        String url = service.storeWorksheet(upload("worksheet.pdf", "application/pdf", original), CONTENT_ID);

        assertTrue(service.isPdfUrl(url), "확장자가 pdf가 아니다: " + url);
        assertEquals("application/pdf", service.contentTypeOf(url));
        assertTrue(service.isPdfUrl(url));
        // 1바이트라도 다르면 어딘가에서 다시 구웠다는 뜻이다
        assertArrayEquals(original, stored(url));
    }

    @Test
    @DisplayName("여러 장짜리 PDF도 통째로 보관한다 — 출력에서 첫 장만 쓴다")
    void 여러장_pdf도_그대로() throws IOException {
        ImageStorageService service = service();
        byte[] original = pdf(3);

        String url = service.storeWorksheet(upload("worksheet.pdf", "application/pdf", original), CONTENT_ID);

        assertArrayEquals(original, stored(url));
        try (PDDocument saved = Loader.loadPDF(stored(url))) {
            assertEquals(3, saved.getNumberOfPages());
        }
    }

    @Test
    @DisplayName("content-type이 비어 와도 확장자로 PDF를 알아본다")
    void 확장자로도_pdf를_알아본다() throws IOException {
        ImageStorageService service = service();

        // 일부 브라우저·OS 조합에서 content-type이 비거나 octet-stream으로 온다
        String url = service.storeWorksheet(upload("워크시트.PDF", "application/octet-stream", pdf(1)), CONTENT_ID);

        assertTrue(service.isPdfUrl(url), "PDF로 인식되지 않았다: " + url);
    }

    @Test
    @DisplayName("이미지로 올린 워크시트도 그대로 저장된다")
    void 이미지는_그대로_저장된다() throws IOException {
        ImageStorageService service = service();
        byte[] original = png();

        String url = service.storeWorksheet(upload("worksheet.png", "image/png", original), CONTENT_ID);

        assertFalse(service.isPdfUrl(url));
        assertEquals("image/png", service.contentTypeOf(url));
        assertArrayEquals(original, stored(url));
    }

    @Test
    @DisplayName("열리지 않는 PDF는 등록 시점에 막는다")
    void 깨진_pdf는_400() {
        ImageStorageService service = service();

        // 등록할 때 걸러야 한다 — 통과시키면 몇 주 뒤 수업 직전 출력에서야 터진다
        Exception400 e = assertThrows(Exception400.class,
                () -> service.storeWorksheet(upload("worksheet.pdf", "application/pdf", "not a pdf".getBytes()), CONTENT_ID));

        assertTrue(e.getMessage().contains("PDF"), "원인을 알 수 없는 메시지: " + e.getMessage());
    }
}
