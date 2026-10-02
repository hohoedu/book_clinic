package com.hohoedu.book_clinic.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.hohoedu.book_clinic._core.file.ImageStorageService;
import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.utils.KstClock;
import com.hohoedu.book_clinic.book.BookService;

/**
 * 활동지 출력 PDF — 2026-10-02.
 *
 * 이 PDF가 곧 종이로 나가므로 장수·이름·날짜가 어긋나면 수업 직전에 선생님이 손으로 고쳐야 한다.
 * 특히 "원본 PDF를 벡터 그대로 옮겼는가"는 눈으로 확인하기 어려워, 옮긴 페이지에서 원본 글자가
 * 다시 텍스트로 읽히는지(=래스터로 굳지 않았는지)를 못박아 둔다. 한때 PNG로 구워 저장했다가
 * 인쇄물이 뭉개져 되돌린 적이 있어, 그 회귀를 막는 것이 이 테스트의 핵심이다.
 */
class WorksheetPrintServiceTest {

    private static final String BODY_TEXT = "WORKSHEET BODY TEXT";

    private ImageStorageService imageStorageService;
    private BookService bookService;
    private WorksheetPrintService service;

    @BeforeEach
    void setUp() {
        imageStorageService = Mockito.mock(ImageStorageService.class);
        bookService = Mockito.mock(BookService.class);
        service = new WorksheetPrintService(imageStorageService, bookService);
    }

    /** 원본 워크시트를 흉내낸 PDF — 본문 한 줄이 벡터 글자로 들어 있다 */
    private byte[] worksheetPdf() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(FontName.HELVETICA), 11);
                cs.newLineAtOffset(60, 700);
                cs.showText(BODY_TEXT);
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private byte[] worksheetPng() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1654, 2339, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private void givenWorksheet(String url, byte[] bytes) throws IOException {
        when(bookService.findWorksheetUrl(Mockito.anyInt())).thenReturn(url);
        when(imageStorageService.isPdfUrl(anyString())).thenReturn(url.endsWith(".pdf"));
        when(imageStorageService.read(anyString())).thenReturn(bytes);
    }

    private String textOfPage(PDDocument doc, int page) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        return stripper.getText(doc);
    }

    @Test
    @DisplayName("학생 수만큼 장이 나오고 각 장에 그 학생 이름이 찍힌다")
    void 여러장_이름별로_찍힌다() throws IOException {
        givenWorksheet("https://host/worksheets/a.pdf", worksheetPdf());
        List<String> names = List.of("김호오", "이서준", "황보지안");

        byte[] pdf = service.build(List.of(
                new WorksheetPrintService.Item(1, names.get(0)),
                new WorksheetPrintService.Item(2, names.get(1)),
                new WorksheetPrintService.Item(3, names.get(2))));

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertEquals(3, doc.getNumberOfPages(), "학생 수만큼 장이 나와야 한다");
            for (int i = 1; i <= 3; i++) {
                String text = textOfPage(doc, i);
                assertTrue(text.contains(names.get(i - 1)), i + "장에 " + names.get(i - 1) + "이(가) 없다: " + text);
                // 원본 내용이 글자로 남아 있어야 한다 — 래스터로 구웠다면 한 글자도 안 읽힌다
                assertTrue(text.contains(BODY_TEXT), i + "장의 원본이 벡터가 아니다: " + text);
            }
        }
    }

    @Test
    @DisplayName("인쇄하는 날의 월·일이 찍힌다")
    void 날짜가_찍힌다() throws IOException {
        givenWorksheet("https://host/worksheets/a.pdf", worksheetPdf());
        LocalDate today = KstClock.today();

        byte[] pdf = service.build(List.of(new WorksheetPrintService.Item(1, "김호오")));

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            String text = textOfPage(doc, 1);
            assertTrue(text.contains(String.valueOf(today.getMonthValue())), "월이 없다: " + text);
            assertTrue(text.contains(String.valueOf(today.getDayOfMonth())), "일이 없다: " + text);
        }
    }

    @Test
    @DisplayName("원본이 이미지인 워크시트도 한 장으로 들어간다")
    void 이미지_워크시트도_나온다() throws IOException {
        givenWorksheet("https://host/worksheets/a.png", worksheetPng());

        byte[] pdf = service.build(List.of(new WorksheetPrintService.Item(1, "김호오")));

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertEquals(1, doc.getNumberOfPages());
            assertTrue(textOfPage(doc, 1).contains("김호오"));
        }
    }

    @Test
    @DisplayName("한 장이 실패해도 나머지는 뽑는다")
    void 일부_실패는_건너뛴다() throws IOException {
        when(bookService.findWorksheetUrl(1)).thenReturn("https://host/worksheets/a.pdf");
        when(bookService.findWorksheetUrl(2)).thenReturn(null);   // 워크시트가 등록되지 않은 책
        when(imageStorageService.isPdfUrl(anyString())).thenReturn(true);
        when(imageStorageService.read(anyString())).thenReturn(worksheetPdf());

        byte[] pdf = service.build(List.of(
                new WorksheetPrintService.Item(1, "김호오"),
                new WorksheetPrintService.Item(2, "이서준")));

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertEquals(1, doc.getNumberOfPages(), "성공한 1장만 나와야 한다");
            assertTrue(textOfPage(doc, 1).contains("김호오"));
        }
    }

    @Test
    @DisplayName("한 장도 못 만들면 400으로 알린다")
    void 전부_실패하면_400() {
        when(bookService.findWorksheetUrl(Mockito.anyInt())).thenReturn(null);

        assertThrows(Exception400.class,
                () -> service.build(List.of(new WorksheetPrintService.Item(1, "김호오"))));
    }

    @Test
    @DisplayName("출력할 목록이 비면 400으로 알린다")
    void 빈_목록은_400() {
        assertThrows(Exception400.class, () -> service.build(List.of()));
    }
}
