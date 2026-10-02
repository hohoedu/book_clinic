package com.hohoedu.book_clinic.monitor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.hohoedu.book_clinic._core.file.ImageStorageService;
import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.utils.KstClock;
import com.hohoedu.book_clinic.book.BookService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 워크시트 출력용 PDF 생성 (2026-10-02).
 *
 * 선생님이 뽑는 활동지 여러 장을 PDF 한 개로 묶어서 내려준다. 브라우저는 그걸 그대로 인쇄한다.
 *
 * [왜 PDF인가] 처음에는 업로드된 PDF를 200dpi PNG로 구워 저장하고 &lt;img&gt;로 인쇄했는데, 래스터로
 * 굳히는 순간 인쇄물의 작은 글자가 눈에 띄게 뭉개졌다. PDF를 원본 그대로 두고 출력도 PDF로 하면
 * 벡터가 프린터 해상도(보통 600dpi) 그대로 찍힌다. 이름·날짜도 이미지에 굽지 않고 PDF 위에
 * 글자로 얹으므로 같이 선명하다.
 *
 * [원본 주소를 숨기는 이유] 호스팅 원본 주소는 브라우저로 내려보내지 않는다. 주소가 노출되면
 * 로그인하지 않은 사람도 URL만 알면 워크시트를 통째로 받아갈 수 있다. 여기서 만든 PDF만 나간다.
 *
 * [복사 차단의 한계] 브라우저 PDF 뷰어의 저장 버튼은 막을 수 없다. 애초에 종이로 뽑으라고 주는
 * 파일이라 여기서 더 조이지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorksheetPrintService {

    private final ImageStorageService imageStorageService;
    private final BookService bookService;

    /** 이름에 쓰는 글씨체 — 학생 화면(주아체)과 같은 것을 종이에서도 쓴다 */
    private static final String NAME_FONT_PATH = "fonts/Jua-Regular.ttf";

    /**
     * 좌표를 실측한 기준 워크시트(A4 세로)의 크기(pt). 실제 워크시트가 다른 크기여도
     * 비율로 환산해 따라가도록, 좌표는 전부 이 크기에 대한 비율로 적는다.
     */
    private static final float REF_WIDTH_PT = 595.276f;

    /**
     * 기입란 자리 — 가로는 페이지 폭, 세로는 페이지 높이에 대한 비율이다.
     * left가 있으면 왼쪽을, right가 있으면 오른쪽 끝을 맞춘다('월'·'일' 앞 숫자는 글자 수가
     * 1~2자로 달라져도 한글에 붙어 있어야 자연스럽다).
     * top은 글자의 세로 중심, fontPt는 기준 폭에서의 글자 크기다.
     *
     * [양식이 다른 워크시트] 이 값들은 표준 학습지 양식에 맞춰 실측한 것이라, 기입란 위치가
     * 다른 워크시트를 등록하면 글자가 엉뚱한 데 찍힌다. 그때는 여기 숫자만 고치면 된다.
     */
    private static final Field NAME = Field.left(0.7438f, 0.0810f, 20f, 0.927f);
    private static final Field MONTH = Field.right(0.2037f, 0.0323f, 11f);
    private static final Field DAY = Field.right(0.1122f, 0.0323f, 11f);

    /** 한 장치고 들어오는 출력 거리 — 어느 책의 워크시트를 누구 이름으로 뽑을지 */
    public record Item(Integer contentId, String studentName) {}

    /**
     * 여러 장을 PDF 한 개로 묶어 돌려준다. 못 불러온 장은 건너뛰고 나머지를 뽑는다 —
     * 20장 중 1장이 실패했다고 전부 막을 이유는 없다. 한 장도 못 만들면 400.
     */
    public byte[] build(List<Item> items) {
        if (items == null || items.isEmpty()) {
            throw new Exception400("출력할 활동지가 없습니다.");
        }

        LocalDate today = KstClock.today();
        List<String> failures = new ArrayList<>();

        try (PDDocument out = new PDDocument(); InputStream fontStream = openNameFont()) {
            PDType0Font nameFont = PDType0Font.load(out, fontStream, true);

            for (Item item : items) {
                try {
                    appendSheet(out, item, nameFont, today);
                } catch (Exception e) {
                    log.warn("워크시트 출력 실패 — contentId={}", item.contentId(), e);
                    failures.add(String.valueOf(item.contentId()));
                }
            }

            if (out.getNumberOfPages() == 0) {
                throw new Exception400("활동지를 불러오지 못했습니다. 도서 정보에 워크시트가 등록되어 있는지 확인해 주세요.");
            }
            if (!failures.isEmpty()) {
                log.warn("활동지 {}장을 빼고 출력합니다 — contentId={}", failures.size(), failures);
            }

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            out.save(bytes);
            return bytes.toByteArray();
        } catch (Exception400 e) {
            throw e;
        } catch (IOException e) {
            log.error("활동지 PDF 생성 실패", e);
            throw new Exception400("활동지를 만들지 못했습니다.");
        }
    }

    /** 워크시트 한 장을 출력 PDF에 덧붙이고 이름·날짜를 찍는다 */
    private void appendSheet(PDDocument out, Item item, PDType0Font nameFont, LocalDate today)
            throws IOException {
        String url = bookService.findWorksheetUrl(item.contentId());
        if (url == null || url.isBlank()) {
            throw new IOException("등록된 워크시트가 없습니다.");
        }

        byte[] source = imageStorageService.read(url);
        PDPage page = imageStorageService.isPdfUrl(url)
                ? appendPdfPage(out, source)
                : appendImagePage(out, source);

        stamp(out, page, nameFont, item.studentName(), today);
    }

    /**
     * 원본 PDF의 첫 페이지를 그대로 옮겨 붙인다.
     *
     * LayerUtility로 폼(Form XObject)으로 들여오는 이유: 원본 문서의 글꼴·그림 같은 리소스까지
     * 깊은 복사로 따라와서, 원본 문서를 닫은 뒤에 저장해도 깨지지 않는다.
     *
     * 첫 장만 쓰는 건 저장 구조가 책 1권당 워크시트 1장이기 때문이다(worksheet_url 컬럼 한 개).
     */
    private PDPage appendPdfPage(PDDocument out, byte[] source) throws IOException {
        try (PDDocument src = Loader.loadPDF(source)) {
            if (src.getNumberOfPages() == 0) throw new IOException("PDF에 페이지가 없습니다.");

            PDPage srcPage = src.getPage(0);
            PDPage page = new PDPage(srcPage.getMediaBox());
            page.setRotation(srcPage.getRotation());
            out.addPage(page);

            if (srcPage.getRotation() != 0) {
                // 돌아간 페이지는 글자 좌표계도 같이 돌아가 이름이 엉뚱한 데 찍힌다. 그런 워크시트가
                // 실제로 올라오면 그때 회전까지 계산하면 되고, 지금은 흔적만 남긴다.
                log.warn("워크시트 PDF가 {}도 회전된 페이지다 — 이름·날짜 위치가 어긋날 수 있다",
                        srcPage.getRotation());
            }

            PDFormXObject form = new LayerUtility(out).importPageAsForm(src, 0);
            try (PDPageContentStream cs = new PDPageContentStream(out, page, AppendMode.APPEND, true, true)) {
                cs.drawForm(form);
            }
            return page;
        }
    }

    /** 이미지로 등록된 워크시트 — A4 한 장에 비율 그대로 꽉 차게 얹는다 */
    private PDPage appendImagePage(PDDocument out, byte[] source) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        out.addPage(page);

        PDImageXObject image = PDImageXObject.createFromByteArray(out, source, "worksheet");
        PDRectangle box = page.getMediaBox();
        float scale = Math.min(box.getWidth() / image.getWidth(), box.getHeight() / image.getHeight());
        float width = image.getWidth() * scale;
        float height = image.getHeight() * scale;

        try (PDPageContentStream cs = new PDPageContentStream(out, page, AppendMode.APPEND, true, true)) {
            cs.drawImage(image, (box.getWidth() - width) / 2, (box.getHeight() - height) / 2, width, height);
        }
        return page;
    }

    /** 이름과 날짜(인쇄 당일)를 기입란에 찍는다 */
    private void stamp(PDDocument out, PDPage page, PDType0Font nameFont, String studentName,
                       LocalDate today) throws IOException {
        try (PDPageContentStream cs = new PDPageContentStream(out, page, AppendMode.APPEND, true, true)) {
            drawField(cs, page, NAME, nameFont, studentName == null ? "" : studentName);
            drawField(cs, page, MONTH, nameFont, String.valueOf(today.getMonthValue()));
            drawField(cs, page, DAY, nameFont, String.valueOf(today.getDayOfMonth()));
        }
    }

    private void drawField(PDPageContentStream cs, PDPage page, Field field, PDFont font, String text)
            throws IOException {
        if (text == null || text.isBlank()) return;

        PDRectangle box = page.getMediaBox();
        float pageWidth = box.getWidth();
        float pageHeight = box.getHeight();

        // 기준 폭(A4)에서 잰 크기를 실제 폭에 맞춰 환산한다
        float size = field.fontPt * pageWidth / REF_WIDTH_PT;
        float textWidth = font.getStringWidth(text) / 1000f * size;

        // 이름이 길면 칸을 넘어 종이 밖으로 흐른다 — 칸에 들어가도록 글자만 줄인다.
        // 그래도 안 들어가는 아주 긴 이름은 더 줄이기보다 읽히는 쪽이 낫다고 보고 절반에서 멈춘다.
        if (field.maxRight != null) {
            float available = (field.maxRight - field.left) * pageWidth;
            if (textWidth > available) {
                size *= Math.max(available / textWidth, 0.5f);
                textWidth = font.getStringWidth(text) / 1000f * size;
            }
        }

        float x = field.left != null
                ? field.left * pageWidth
                : pageWidth * (1 - field.right) - textWidth;

        // top은 글자의 세로 중심이다. PDF의 y는 아래에서 재므로 뒤집고, 대문자 높이의 절반만큼
        // 내려 기준선을 잡아야 원본의 '이름 :'·'월'·'일'과 눈높이가 맞는다.
        float centerY = pageHeight * (1 - field.top);
        float capHeight = font.getFontDescriptor().getCapHeight() / 1000f * size;
        float y = centerY - capHeight / 2;

        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    /** 글씨체 파일은 jar 안에 같이 들어간다 — 서버에 한글 폰트가 깔려 있는지에 기대지 않는다 */
    private InputStream openNameFont() throws IOException {
        ClassPathResource resource = new ClassPathResource(NAME_FONT_PATH);
        if (!resource.exists()) {
            log.error("이름 글씨체 파일이 없습니다 — {}", NAME_FONT_PATH);
            throw new Exception400("이름 글씨체 파일이 없어 활동지를 만들지 못했습니다. 관리자에게 문의해 주세요.");
        }
        return resource.getInputStream();
    }

    /**
     * 기입란 한 칸. left/right 중 하나만 채워진다 — left면 왼쪽 맞춤, right면 오른쪽 맞춤이다.
     * maxRight는 글자가 넘지 말아야 할 오른쪽 한계(이름 칸 흰 박스의 끝)로, 왼쪽 맞춤에만 쓴다.
     */
    private record Field(Float left, Float right, float top, float fontPt, Float maxRight) {
        static Field left(float left, float top, float fontPt, float maxRight) {
            return new Field(left, null, top, fontPt, maxRight);
        }

        static Field right(float right, float top, float fontPt) {
            return new Field(null, right, top, fontPt, null);
        }
    }
}
