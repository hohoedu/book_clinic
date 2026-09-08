package com.hohoedu.book_clinic.book;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.handler.exception.Exception404;
import com.hohoedu.book_clinic.book._dto.BookReqDTO;
import com.hohoedu.book_clinic.book._dto.BookRespDTO;
import com.hohoedu.book_clinic.common.code.CodeRepository;
import com.hohoedu.book_clinic.common.code._dto.CodeRespDTO;
import com.hohoedu.book_clinic.student.StudentRepository;
import com.hohoedu.book_clinic.student.model.Student;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 도서 비즈니스 로직 서비스
 * - 마스터 도서(content), 실물 도서(item, bcode+센터당 1행 + qty/loaned_qty 카운터. 2026-07-29
 * 재설계로 사본 1행=1권 모델을 대체) 처리
 * - 삭제/복구는 저장 프로시저(sp_delete_book, sp_restore_book) 호출
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookService {

    // 분류(content_type) 코드 → content_detail gubun (01=교과연계, 04=기관추천, 05=인증수상작)
    private static final Map<String, String> EXTRA_DETAIL_GUBUN_BY_CONTENT_TYPE = Map.of("01", "C", "04", "R", "05",
            "A");
    private static final List<String> ALL_DETAIL_GUBUNS = List.of("C", "R", "A");

    private final BookRepository bookRepository;
    private final StudentRepository studentRepository;
    private final CodeRepository codeRepository;

    /** 마스터 도서 등록 */
    @Transactional
    public void registerContent(BookReqDTO.RegisterReqDTO reqDTO, String registeredBy) {
        bookRepository.registerContent(reqDTO);
        saveExtraDetail(reqDTO.getContentId(), reqDTO.getContentType(), reqDTO.getExtraDetail());
        saveCardPath(reqDTO.getContentId(), reqDTO.getCardUrl(), registeredBy);
    }

    // ===================== 엑셀 일괄 등록 (2026-09-08) =====================

    /**
     * 일괄 등록 템플릿 컬럼 — 초1_교과연계_28권.xlsx 양식을 따르되 맨 앞에 content_id(수정 금지)를 둔다.
     * 파싱은 위치가 아닌 헤더명으로 하므로 순서가 바뀌어도 동작한다.
     */
    private static final String[] IMPORT_HEADERS = {
            "content_id", "NO", "학년", "도서분류", "도서명", "연계교과/추천기관/수상명", "장르", "난이도", "출판사", "해시태그", "도서소개", "독서시간" };
    private static final int[] IMPORT_WIDTHS = {
            2800, 1800, 2600, 3400, 12000, 5800, 3200, 2400, 5200, 8000, 24000, 2800 };
    // IMPORT_HEADERS 기준 드롭다운 열 인덱스
    private static final int COL_CONTENT_TYPE = 3;
    private static final int COL_GENRE = 6;
    private static final int COL_DIFFICULTY = 7;

    /** 엑셀 헤더명(공백 제거) → 필드. content_id가 있으면 그 행은 UPDATE, 비어 있으면 INSERT */
    private static final Map<String, String> HEADER_ALIASES = Map.ofEntries(
            Map.entry("content_id", "contentId"), Map.entry("contentid", "contentId"), Map.entry("관리번호", "contentId"),
            Map.entry("학년", "schoolYear"),
            Map.entry("도서분류", "contentType"), Map.entry("분류", "contentType"),
            Map.entry("도서명", "title"), Map.entry("제목", "title"),
            Map.entry("저자", "author"), Map.entry("작가", "author"), Map.entry("지은이", "author"),
            Map.entry("장르", "genre"),
            Map.entry("난이도", "difficulty"),
            Map.entry("출판사", "publisher"),
            Map.entry("해시태그", "keywords"), Map.entry("키워드", "keywords"), Map.entry("태그", "keywords"),
            Map.entry("도서소개", "summary"), Map.entry("줄거리", "summary"), Map.entry("요약", "summary"), Map.entry("내용", "summary"),
            Map.entry("독서시간", "readingTime"), Map.entry("예상독서시간", "readingTime"),
            Map.entry("부가정보(연계교과/추천기관/수상명)", "extraDetail"), Map.entry("부가정보", "extraDetail"),
            Map.entry("연계교과/추천기관/수상명", "extraDetail"),
            Map.entry("연계교과", "extraDetail"), Map.entry("추천기관", "extraDetail"),
            Map.entry("추천기관명", "extraDetail"), Map.entry("수상명", "extraDetail"), Map.entry("수상작", "extraDetail"));

    /** 파싱된 한 행 — contentId가 있으면 수정, 없으면 신규 */
    private record ParsedBookRow(Integer contentId, BookReqDTO.RegisterReqDTO dto, int humanRow) {}

    /**
     * 엑셀(xlsx)로 마스터 도서를 일괄 등록/수정한다.
     * - A열 content_id가 채워진 행은 그 도서를 UPDATE, 비어 있는 행은 INSERT (도서명 매칭을 쓰지 않는다).
     * - content_id가 있어도 <b>바뀐 값이 하나도 없으면 건드리지 않는다</b>(unchanged로 집계) — 전체 템플릿을
     *   그대로 다시 올려도 실제 변경분만 반영되고 수정 로그가 무의미하게 쌓이지 않는다.
     * - 학년/분류/장르는 드롭다운으로 고른 한글 코드명을 erp_bookstore_code 코드값으로 변환한다.
     *     mode = "check"        : 저장하지 않고 신규/수정/변경없음 건수만 집계 (offset/limit 무시, 파일 전체)
     *     mode = "upsert"(기본) : rows[offset, offset+limit) 구간만 실제 반영 — 프런트가 나눠 호출하며 진행률 표시
     */
    @Transactional
    public BookRespDTO.ImportResultDTO importContents(MultipartFile file, String mode, int offset, int limit,
            String uploadedBy) throws IOException {
        Map<String, String> gradeByName = nameToCode("S");
        Map<String, String> typeByName = nameToCode("C");
        Map<String, String> genreByName = nameToCode("G");

        BookRespDTO.ImportResultDTO result = new BookRespDTO.ImportResultDTO();
        List<ParsedBookRow> rows = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                Map<String, Integer> col = new HashMap<>();
                int headerRowNum = findHeaderRow(sheet, col);
                if (headerRowNum < 0) continue; // 도서명 헤더가 없는 시트는 건너뜀
                result.getSheets().add(sheet.getSheetName());

                // 학년별 시트 템플릿: 행의 학년 칸이 비어 있으면 시트명("초1" 등)으로 보정
                String sheetGradeCode = gradeByName.get(sheet.getSheetName().replaceAll("\\s+", ""));

                for (Row row : sheet) {
                    if (row.getRowNum() <= headerRowNum) continue;
                    String title = cellStr(row, col.get("title"));
                    String idRaw = cellStr(row, col.get("contentId"));
                    if (title == null && idRaw == null) continue; // 빈 행

                    int humanRow = row.getRowNum() + 1;
                    try {
                        if (title == null)
                            throw new IllegalArgumentException(humanRow + "행: 도서명이 비어 있습니다.");

                        Integer contentId = null;
                        if (idRaw != null) {
                            try {
                                contentId = Integer.valueOf(idRaw.replaceAll("[^0-9]", ""));
                            } catch (NumberFormatException e) {
                                throw new IllegalArgumentException(humanRow + "행: content_id 형식 오류 - " + idRaw);
                            }
                        }

                        String gradeRaw = cellStr(row, col.get("schoolYear"));

                        BookReqDTO.RegisterReqDTO dto = new BookReqDTO.RegisterReqDTO();
                        dto.setTitle(title);
                        dto.setAuthor(cellStr(row, col.get("author")));
                        dto.setSchoolYear(gradeRaw != null
                                ? resolveGrade(gradeRaw, gradeByName, humanRow)
                                : sheetGradeCode);
                        dto.setContentType(resolveByName(cellStr(row, col.get("contentType")), typeByName, "분류", humanRow));
                        dto.setGenre(resolveByName(cellStr(row, col.get("genre")), genreByName, "장르", humanRow));
                        dto.setDifficulty(cellStr(row, col.get("difficulty")));
                        dto.setPublisher(cellStr(row, col.get("publisher")));
                        dto.setKeywords(normalizeKeywords(cellStr(row, col.get("keywords"))));
                        dto.setSummary(cellStr(row, col.get("summary")));
                        dto.setReadingTime(cellStr(row, col.get("readingTime")));
                        dto.setExtraDetail(cellStr(row, col.get("extraDetail")));
                        // 상태(사용여부)는 템플릿에 없다 — 신규는 "Y", 기존 도서는 건드리지 않는다(아래 반영 루프에서 처리)
                        rows.add(new ParsedBookRow(contentId, dto, humanRow));
                    } catch (IllegalArgumentException e) {
                        result.getErrors().add(e.getMessage());
                    }
                }
            }
        }

        result.setTotal(rows.size());
        boolean checkOnly = "check".equals(mode);

        // content_id → 현재 DB 값 (바뀐 게 있는지 비교용)
        Map<Integer, BookRespDTO.ContentRespDTO> currentById = new HashMap<>();
        for (BookRespDTO.ContentRespDTO c : bookRepository.searchContents(null, null, null, null, null, null, null)) {
            currentById.put(c.getContentId(), c);
        }

        int from = checkOnly ? 0 : Math.max(0, offset);
        int to = checkOnly ? rows.size() : Math.min(rows.size(), from + Math.max(0, limit));

        for (int i = from; i < to; i++) {
            ParsedBookRow r = rows.get(i);
            if (r.contentId() != null) {
                BookRespDTO.ContentRespDTO cur = currentById.get(r.contentId());
                if (cur == null) {
                    result.getErrors().add(r.humanRow() + "행: content_id " + r.contentId() + " 도서를 찾을 수 없습니다.");
                    continue;
                }
                if (!hasChange(r.dto(), cur)) {
                    result.setUnchanged(result.getUnchanged() + 1);
                    continue;
                }
                if (!checkOnly) updateContent(toUpdateReqDTO(r.dto(), r.contentId()), uploadedBy);
                result.setUpdated(result.getUpdated() + 1);
            } else {
                r.dto().setState("Y");
                if (!checkOnly) registerContent(r.dto(), uploadedBy);
                result.setInserted(result.getInserted() + 1);
            }
        }
        result.setProcessed(to);
        return result;
    }

    /** 템플릿이 실어온 값(빈 칸=null=그대로 두기)과 현재 DB 값을 비교해 하나라도 다르면 true */
    private boolean hasChange(BookReqDTO.RegisterReqDTO d, BookRespDTO.ContentRespDTO c) {
        return valueChanged(d.getTitle(), c.getOriginalTitle())
                || valueChanged(d.getSchoolYear(), c.getSchoolyear())
                || valueChanged(d.getContentType(), c.getContentType())
                || valueChanged(d.getGenre(), c.getGenre())
                || valueChanged(d.getDifficulty(), c.getDifficulty())
                || valueChanged(d.getPublisher(), c.getPublisher())
                || valueChanged(d.getKeywords(), c.getKeywords())
                || valueChanged(d.getSummary(), c.getSummary())
                || valueChanged(d.getReadingTime(), c.getReadingTime())
                || valueChanged(d.getExtraDetail(), c.getExtraDetailName());
    }

    /** incoming이 null이면(=빈 칸) 변경 아님. 값이 있고 현재 값과 다르면 변경 */
    private boolean valueChanged(String incoming, String current) {
        if (incoming == null) return false;
        return !incoming.strip().equals(current == null ? "" : current.strip());
    }

    /**
     * 일괄 등록/수정 템플릿(xlsx) 생성 — 초1_교과연계_28권.xlsx 양식(제목행 + 헤더행 + NO/학년/도서분류/도서명/…)을
     * 최대한 따르되, 맨 앞에 회색 content_id 열을 둔다. 기존 도서를 <b>학년별 시트</b>로 나눠 채워 내려주고,
     * 사용자는 값을 고치거나(그 행 UPDATE) 시트 맨 아래에 content_id 없이 새 행을 추가한다(INSERT).
     * 도서분류/장르/난이도/학년은 드롭다운으로 고르게 해 표기 흔들림을 막는다.
     */
    public byte[] buildImportTemplateWorkbook() {
        List<BookRespDTO.ContentRespDTO> books = bookRepository.searchContents(null, null, null, null, null, null, null);
        Map<String, List<BookRespDTO.ContentRespDTO>> byGradeCode = books.stream()
                .collect(Collectors.groupingBy(b -> b.getSchoolyear() == null ? "" : b.getSchoolyear()));

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // 상단(제목·헤더) 배경색 #EBFFE4
            XSSFColor topColor = new XSSFColor(new byte[] { (byte) 0xEB, (byte) 0xFF, (byte) 0xE4 }, null);

            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            XSSFCellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);
            titleStyle.setFillForegroundColor(topColor);
            titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);
            titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(topColor);
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setThinBorder(headerStyle);

            CellStyle bodyStyle = workbook.createCellStyle();
            bodyStyle.setAlignment(HorizontalAlignment.CENTER);
            bodyStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setThinBorder(bodyStyle);

            CellStyle wrapStyle = workbook.createCellStyle();
            wrapStyle.cloneStyleFrom(bodyStyle);
            wrapStyle.setWrapText(true);
            wrapStyle.setAlignment(HorizontalAlignment.LEFT); // 긴 도서소개는 왼쪽정렬이 읽기 쉬움

            CellStyle lockedStyle = workbook.createCellStyle();
            lockedStyle.cloneStyleFrom(bodyStyle);
            lockedStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            lockedStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            String[] typeNames = codeNames("C");
            String[] genreNames = codeNames("G");
            String[] gradeNames = codeNames("S");

            List<CodeRespDTO.BookstoreCodeDTO> grades = codeRepository.findBookstoreCodesByGubun("S");
            for (CodeRespDTO.BookstoreCodeDTO grade : grades) {
                writeTemplateSheet(workbook, grade.getCodeName(), grade.getCodeName(),
                        byGradeCode.getOrDefault(grade.getCode(), List.of()),
                        titleStyle, headerStyle, bodyStyle, wrapStyle, lockedStyle,
                        typeNames, genreNames, gradeNames);
            }
            // 학년 코드가 비어 있거나 알 수 없는 도서는 "기타" 시트로 (있을 때만)
            List<BookRespDTO.ContentRespDTO> etc = new ArrayList<>();
            for (Map.Entry<String, List<BookRespDTO.ContentRespDTO>> e : byGradeCode.entrySet()) {
                boolean known = grades.stream().anyMatch(g -> g.getCode().equals(e.getKey()));
                if (!known) etc.addAll(e.getValue());
            }
            if (!etc.isEmpty()) {
                writeTemplateSheet(workbook, "기타", "학년 미지정", etc,
                        titleStyle, headerStyle, bodyStyle, wrapStyle, lockedStyle,
                        typeNames, genreNames, gradeNames);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("템플릿 생성 중 오류가 발생했습니다.", e);
        }
    }

    /** 학년 시트 하나: 0행=제목(병합) / 1행=헤더 / 2행~=도서. 새 행 추가용으로 드롭다운은 아래로 넉넉히 건다 */
    private void writeTemplateSheet(XSSFWorkbook workbook, String sheetName, String gradeLabel,
            List<BookRespDTO.ContentRespDTO> rows, CellStyle titleStyle, CellStyle headerStyle,
            CellStyle bodyStyle, CellStyle wrapStyle, CellStyle lockedStyle,
            String[] typeNames, String[] genreNames, String[] gradeNames) {
        Sheet sheet = workbook.createSheet(sheetName);
        int cols = IMPORT_HEADERS.length;
        sheet.setDefaultRowHeightInPoints(70f); // 새로 추가하는 행도 70pt로 (도서소개 줄바꿈 대비)

        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(28f);
        for (int c = 0; c < cols; c++) titleRow.createCell(c).setCellStyle(titleStyle);
        titleRow.getCell(0).setCellValue(gradeLabel + " 도서 정보");
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, cols - 1));

        Row headerRow = sheet.createRow(1);
        headerRow.setHeightInPoints(20f);
        for (int c = 0; c < cols; c++) {
            Cell cell = headerRow.createCell(c);
            cell.setCellValue(IMPORT_HEADERS[c]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(c, IMPORT_WIDTHS[c]);
        }

        int rowIdx = 2;
        int no = 1;
        for (BookRespDTO.ContentRespDTO b : rows) {
            Row row = sheet.createRow(rowIdx++);
            row.setHeightInPoints(70f);
            Cell idCell = row.createCell(0);
            if (b.getContentId() != null) idCell.setCellValue(b.getContentId());
            idCell.setCellStyle(lockedStyle);
            putCell(row, 1, String.valueOf(no++), bodyStyle);
            putCell(row, 2, nvl(b.getSchoolyearName()), bodyStyle);
            putCell(row, 3, nvl(b.getContentTypeName()), bodyStyle);
            putCell(row, 4, nvl(b.getOriginalTitle()), bodyStyle);
            putCell(row, 5, nvl(b.getExtraDetailName()), bodyStyle);
            putCell(row, 6, nvl(b.getGenreName()), bodyStyle);
            putCell(row, 7, nvl(b.getDifficulty()), bodyStyle);
            putCell(row, 8, nvl(b.getPublisher()), bodyStyle);
            putCell(row, 9, nvl(b.getKeywords()), bodyStyle);
            putCell(row, 10, nvl(b.getSummary()), wrapStyle);
            putCell(row, 11, nvl(b.getReadingTime()), bodyStyle);
        }

        int lastRow = Math.max(rowIdx, 3) + 300; // 새로 추가할 행까지 드롭다운 적용
        DataValidationHelper dv = sheet.getDataValidationHelper();
        addImportDropdown(dv, sheet, gradeNames, 2, lastRow);
        addImportDropdown(dv, sheet, typeNames, COL_CONTENT_TYPE, lastRow);
        addImportDropdown(dv, sheet, genreNames, COL_GENRE, lastRow);
        addImportDropdown(dv, sheet, new String[] { "상", "중", "하" }, COL_DIFFICULTY, lastRow);

        sheet.createFreezePane(2, 2); // content_id·NO 열 + 제목·헤더 행 고정
    }

    private void putCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void addImportDropdown(DataValidationHelper dv, Sheet sheet, String[] items, int col, int lastRow) {
        if (items.length == 0) return;
        DataValidationConstraint constraint = dv.createExplicitListConstraint(items);
        DataValidation validation = dv.createValidation(constraint, new CellRangeAddressList(2, lastRow, col, col));
        validation.setSuppressDropDownArrow(true);
        validation.setShowErrorBox(true);
        sheet.addValidationData(validation);
    }

    private String[] codeNames(String gubun) {
        return codeRepository.findBookstoreCodesByGubun(gubun).stream()
                .map(CodeRespDTO.BookstoreCodeDTO::getCodeName)
                .filter(n -> n != null && !n.isBlank())
                .toArray(String[]::new);
    }

    /** 헤더 행(첫 15행 안에서 "도서명" 셀이 있는 행)을 찾아 col 맵에 헤더명→열번호를 채우고, 그 행 번호를 돌려준다 */
    private int findHeaderRow(Sheet sheet, Map<String, Integer> col) {
        for (Row row : sheet) {
            if (row.getRowNum() > 15) break;
            Map<String, Integer> found = new HashMap<>();
            for (Cell cell : row) {
                String raw = cellText(cell);
                if (raw == null) continue;
                String field = HEADER_ALIASES.get(raw.replaceAll("\\s+", "").toLowerCase());
                if (field == null) field = HEADER_ALIASES.get(raw.replaceAll("\\s+", ""));
                if (field != null) found.putIfAbsent(field, cell.getColumnIndex());
            }
            // "도서명"만 있고 다른 인식 가능한 열이 없는 시트(예: 확인자료 링크 시트)는 데이터 시트로 보지 않는다
            if (found.containsKey("title") && found.size() >= 2) {
                col.putAll(found);
                return row.getRowNum();
            }
        }
        return -1;
    }

    /** erp_bookstore_code gubun의 (코드명 → 코드값) 맵 */
    private Map<String, String> nameToCode(String gubun) {
        Map<String, String> map = new HashMap<>();
        for (CodeRespDTO.BookstoreCodeDTO c : codeRepository.findBookstoreCodesByGubun(gubun)) {
            if (c.getCodeName() != null) map.put(c.getCodeName().replaceAll("\\s+", ""), c.getCode());
        }
        return map;
    }

    /** 한글 코드명을 코드값으로. 값이 비어 있으면 null, 매핑 실패면 그 행을 건너뛰도록 예외 */
    private String resolveByName(String name, Map<String, String> byName, String label, int humanRow) {
        if (name == null) return null;
        String code = byName.get(name.replaceAll("\\s+", ""));
        if (code == null) throw new IllegalArgumentException(humanRow + "행: 알 수 없는 " + label + " 값 - " + name);
        return code;
    }

    /** 학년: "교과연계 시트의 1", "01학년", "초1", "01" 등을 학년 코드(01~07)로 맞춘다 */
    private String resolveGrade(String raw, Map<String, String> gradeByName, int humanRow) {
        if (raw == null) return null;
        String key = raw.replaceAll("\\s+", "");
        if (gradeByName.containsKey(key)) return gradeByName.get(key);           // "초1"
        String digits = key.replaceAll("[^0-9]", "");
        if (!digits.isEmpty()) {
            int n = Integer.parseInt(digits);
            if (n >= 1 && n <= 7) return String.format("%02d", n);              // "1" / "01학년"
        }
        throw new IllegalArgumentException(humanRow + "행: 알 수 없는 학년 값 - " + raw);
    }

    /** "#가을 #운동회 #공동체" → "가을,운동회,공동체" (이미 콤마 구분이면 그대로) */
    private String normalizeKeywords(String raw) {
        if (raw == null) return null;
        if (raw.contains("#")) {
            return raw.replace("#", " ").trim().replaceAll("\\s+", ",");
        }
        return raw;
    }

    private BookReqDTO.UpdateReqDTO toUpdateReqDTO(BookReqDTO.RegisterReqDTO src, Integer contentId) {
        BookReqDTO.UpdateReqDTO dto = new BookReqDTO.UpdateReqDTO();
        dto.setContentId(contentId);
        dto.setTitle(src.getTitle());
        dto.setAuthor(src.getAuthor());
        dto.setGenre(src.getGenre());
        dto.setContentType(src.getContentType());
        dto.setSchoolYear(src.getSchoolYear());
        dto.setSummary(src.getSummary());
        dto.setKeywords(src.getKeywords());
        dto.setState(src.getState());
        dto.setPublisher(src.getPublisher());
        dto.setReadingTime(src.getReadingTime());
        dto.setDifficulty(src.getDifficulty());
        dto.setExtraDetail(src.getExtraDetail());
        return dto;
    }

    /** 엑셀 셀 → 문자열 (null/공백이면 null). 숫자셀은 정수로 변환 */
    private String cellStr(Row row, Integer colIndex) {
        if (colIndex == null) return null;
        return cellText(row.getCell(colIndex));
    }

    private String cellText(Cell cell) {
        if (cell == null) return null;
        String value;
        if (cell.getCellType() == CellType.NUMERIC) {
            double d = cell.getNumericCellValue();
            value = (d == Math.rint(d)) ? String.valueOf((long) d) : String.valueOf(d);
        } else if (cell.getCellType() == CellType.BOOLEAN) {
            value = String.valueOf(cell.getBooleanCellValue());
        } else if (cell.getCellType() == CellType.FORMULA) {
            value = cell.getStringCellValue();
        } else {
            value = cell.getStringCellValue();
        }
        value = value == null ? null : value.replace("\r\n", "\n").replace("\r", "\n").trim();
        return (value == null || value.isEmpty()) ? null : value;
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
        saveCardPath(reqDTO.getContentId(), reqDTO.getCardUrl(), updatedBy);
    }

    /**
     * 수집 카드 이미지 경로 저장 (2026-09-02) — 완독 시 지급되는 카드의 그림.
     * 표지와 다른 이미지라 erp_bookstore_card_path에 따로 둔다(도서 마스터는 건드리지 않는다).
     *
     * cardUrl의 세 가지 의미를 구분한다. extraDetail과 달리 "안 보냄"과 "지움"을 나눠야 하는데,
     * 상태 토글처럼 일부 필드만 보내는 부분 수정이 기존 카드를 지워버리면 안 되기 때문이다.
     *   null        → 이번 요청에서 카드는 건드리지 않는다 (부분 수정)
     *   빈 문자열    → 카드 이미지 제거 (이후 화면은 기본 카드로 폴백)
     *   값 있음      → 등록/교체
     */
    private void saveCardPath(Integer contentId, String cardUrl, String registeredBy) {
        if (contentId == null || cardUrl == null) return;
        if (cardUrl.isBlank()) {
            bookRepository.deleteCardPath(contentId);
        } else {
            bookRepository.upsertCardPath(contentId, cardUrl.trim(), registeredBy);
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

    /** 학년별 도서 목록 엑셀 다운로드 — 학년(codeNm)별로 시트를 나누고, 시트 맨 위에 "OO 도서목록" 제목을 붙인다.
     *  content_id 대신 시트 내 순번(NO)을 매긴다 */
    public byte[] buildGradeExcelWorkbook() {
        Map<String, List<BookRespDTO.GradeListRespDTO>> byGrade = bookRepository.findContentsForGradeExcel().stream()
                .collect(Collectors.groupingBy(BookRespDTO.GradeListRespDTO::getSchoolyearName, LinkedHashMap::new, Collectors.toList()));

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle titleStyle = createGradeExcelTitleStyle(workbook);
            CellStyle headerStyle = createGradeExcelHeaderStyle(workbook);
            CellStyle bodyStyle = createGradeExcelBodyStyle(workbook);

            byGrade.forEach((gradeName, rows) -> {
                Sheet sheet = workbook.createSheet(gradeName);
                writeGradeExcelTitle(sheet, gradeName, titleStyle);
                writeGradeExcelHeader(sheet, headerStyle);
                writeGradeExcelRows(sheet, rows, bodyStyle);
                sheet.createFreezePane(0, 2);
                applyGradeExcelPrintSetup(workbook, sheet, rows.size());

                sheet.setColumnWidth(0, 6 * 256);
                sheet.setColumnWidth(1, 30 * 256);
                sheet.setColumnWidth(2, 16 * 256);
                sheet.setColumnWidth(3, 10 * 256);
            });

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("엑셀 생성 중 오류가 발생했습니다.", e);
        }
    }

    private CellStyle createGradeExcelTitleStyle(XSSFWorkbook workbook) {
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 16);
        titleFont.setColor(IndexedColors.WHITE.getIndex());

        CellStyle style = workbook.createCellStyle();
        style.setFont(titleFont);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.BLUE_GREY.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createGradeExcelHeaderStyle(XSSFWorkbook workbook) {
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);

        CellStyle style = workbook.createCellStyle();
        style.setFont(headerFont);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setThinBorder(style);
        return style;
    }

    private CellStyle createGradeExcelBodyStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setThinBorder(style);
        return style;
    }

    private void setThinBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    /** 시트 맨 위 1행에 "OO 도서목록" 제목을 컬럼 전체 병합해서 넣는다 */
    /** 인쇄 시 페이지마다 제목/헤더(0~1행)가 반복되고, A4 한 페이지 너비에 맞춰 인쇄되도록 설정한다 */
    private void applyGradeExcelPrintSetup(XSSFWorkbook workbook, Sheet sheet, int rowCount) {
        int sheetIndex = workbook.getSheetIndex(sheet);
        int lastRow = rowCount + 1; // 0=제목, 1=헤더, 2..=데이터

        workbook.setPrintArea(sheetIndex, 0, 3, 0, lastRow);
        sheet.setRepeatingRows(new CellRangeAddress(0, 1, -1, -1));

        PrintSetup printSetup = sheet.getPrintSetup();
        printSetup.setPaperSize(PrintSetup.A4_PAPERSIZE);
        printSetup.setLandscape(false);
        printSetup.setFitWidth((short) 1);
        printSetup.setFitHeight((short) 0);
        sheet.setFitToPage(true);
        sheet.setHorizontallyCenter(true);
    }

    private void writeGradeExcelTitle(Sheet sheet, String gradeName, CellStyle titleStyle) {
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(28f);
        for (int c = 0; c < 4; c++) {
            Cell cell = titleRow.createCell(c);
            cell.setCellStyle(titleStyle);
        }
        titleRow.getCell(0).setCellValue(gradeName + " 도서목록");
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));
    }

    private void writeGradeExcelHeader(Sheet sheet, CellStyle headerStyle) {
        Row row = sheet.createRow(1);
        String[] headers = { "NO", "도서명", "저자", "학년" };
        for (int c = 0; c < headers.length; c++) {
            Cell cell = row.createCell(c);
            cell.setCellValue(headers[c]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void writeGradeExcelRows(Sheet sheet, List<BookRespDTO.GradeListRespDTO> rows, CellStyle bodyStyle) {
        int rowIdx = 2;
        int no = 1;
        for (BookRespDTO.GradeListRespDTO r : rows) {
            Row row = sheet.createRow(rowIdx++);
            Cell noCell = row.createCell(0);
            noCell.setCellValue(no++);
            noCell.setCellStyle(bodyStyle);
            Cell titleCell = row.createCell(1);
            titleCell.setCellValue(nvl(r.getOriginalTitle()));
            titleCell.setCellStyle(bodyStyle);
            Cell authorCell = row.createCell(2);
            authorCell.setCellValue(nvl(r.getAuthor()));
            authorCell.setCellStyle(bodyStyle);
            Cell gradeCell = row.createCell(3);
            gradeCell.setCellValue(nvl(r.getSchoolyearName()));
            gradeCell.setCellStyle(bodyStyle);
        }
    }

    private String nvl(String value) {
        return value == null ? "" : value;
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

    /**
     * 학생의 현재 대여 중(LOANED) 도서를 반납 처리한다(없으면 조용히 넘어감) — 퇴실 처리
     * (MonitorService.exitSession)가 호출한다. 완독 전이라도 학생이 클리닉을 나가면 그 사이 다른
     * 학생이 재고를 못 쓰는 문제를 막기 위해, 완독 여부와 무관하게 퇴실 시점에 반납한다(2026-07-30).
     * 재입실하면 ClinicService가 같은 책을 다시 대여해 이어 읽을 수 있게 한다.
     */
    @Transactional
    public void returnActiveLoanByStudent(String studentId) {
        BookRespDTO.ItemLoanRespDTO loan = bookRepository.findActiveLoanByStudent(studentId);
        if (loan == null) return;
        bookRepository.updateLoanReturned(loan.getLoanId());
        bookRepository.markItemReturned(loan.getItemId());
    }

    /**
     * 학생에게 그 책의 실물 한 권을 확보해준다 — 문제풀이 기록을 되돌릴 때(MonitorService.resetQuiz)
     * 호출한다. 되돌린 책이 완독으로 이미 반납된 상태면 그 사이 다른 학생이 가져갔을 수 있어서,
     * 원래 판본을 1순위로 잡되 없으면 같은 센터의 다른 사본으로 대체한다.
     *
     * 학생이 이미 무언가를 대여 중이면(되돌린 책을 아직 들고 있는 경우) 그 대여를 그대로 인정한다 —
     * 여기서 또 잡으면 한 학생이 두 권을 물고 있게 된다.
     *
     * @return 확보된 item_id (원래 판본 또는 대체 사본). 대여 가능한 사본이 한 권도 없으면 null
     */
    @Transactional
    public Integer secureCopyForStudent(String studentId, Integer itemId) {
        BookRespDTO.ItemLoanRespDTO active = bookRepository.findActiveLoanByStudent(studentId);
        if (active != null) return active.getItemId();

        Integer securedItemId = bookRepository.reserveCopyForContentOf(itemId);
        if (securedItemId == null) return null;
        bookRepository.insertItemLoan(securedItemId, studentId);
        return securedItemId;
    }

    /**
     * 추천 취소(책이 없음 / 못 읽을 정도로 훼손) — 그 한 권을 재고에서 뺀다 (2026-09-02).
     *
     * 반납(returnActiveLoanByStudent)과 결정적으로 다른 점은 loaned_qty만 줄이는 게 아니라 그 한 권을
     * lost_qty로 옮긴다는 것이다. 가용 재고(qty - loaned_qty - lost_qty)에서 빠져야 추천 후보 쿼리
     * (ClinicMapper.pickNextItem)가 그 판본을 다시 뽑지 않는다 — 없는 책/훼손된 책이 다음 학생에게
     * 또 추천되는 것을 막는 게 이 처리의 핵심이다.
     *
     * 같은 책의 사본이 2권 이상이면 남은 사본은 그대로 후보로 남는다(훼손된 건 지금 이 한 권뿐이므로
     * 그게 맞다). 그래서 취소 직후 같은 책이 다시 추천될 수 있는데, 그건 DB상 멀쩡한 사본이 더 있다는
     * 뜻이라 직원이 한 번 더 누르면 그 사본도 차감된다.
     *
     * @param itemId 추천이 가리키던 판본 — 학생의 대여가 이미 풀린 경우(퇴실 등)의 차감 대상
     * @return 실제로 재고에서 뺐으면 true (이미 재고가 0이라 뺄 게 없었으면 false)
     */
    @Transactional
    public boolean loseCopy(String studentId, Integer itemId, String staffName) {
        BookRespDTO.ItemLoanRespDTO loan = bookRepository.findActiveLoanByStudent(studentId);
        Integer targetItemId = loan != null ? loan.getItemId() : itemId;
        if (targetItemId == null) return false;

        bookRepository.archiveItemByIdForUpdate(targetItemId, staffName);
        if (loan != null) {
            bookRepository.updateLoanLost(loan.getLoanId());
            boolean removed = bookRepository.markLoanedItemLost(targetItemId) > 0;
            log.info("추천 취소로 대여분을 분실 처리했습니다 — studentId={}, itemId={}, 재고반영={}",
                    studentId, targetItemId, removed);
            return removed;
        }
        // 대여가 이미 풀린 상태(퇴실 등) — 가용 재고가 남아 있을 때만 한 권 뺀다
        boolean removed = bookRepository.markAvailableItemLost(targetItemId) > 0;
        log.info("추천 취소로 가용 재고 한 권을 분실 처리했습니다 — studentId={}, itemId={}, 재고반영={}",
                studentId, targetItemId, removed);
        return removed;
    }

    /**
     * 분실/훼손 처리 되돌리기 — "책이 없다"로 뺐다가 나중에 책장에서 찾은 경우 (2026-09-02).
     * 이 경로가 없으면 loseCopy로 뺀 재고가 영영 안 살아난다.
     *
     * @return 되돌렸으면 true (되돌릴 분실분이 없으면 false)
     */
    @Transactional
    public boolean restoreLostCopy(String bcode, String centerCode, String staffName) {
        Integer itemId = bookRepository.findItemIdByBcodeCenter(bcode, centerCode);
        if (itemId == null) return false;
        bookRepository.archiveItemByIdForUpdate(itemId, staffName);
        boolean restored = bookRepository.restoreLostCopy(bcode, centerCode) > 0;
        log.info("분실/훼손 재고를 복구했습니다 — bcode={}, centerCode={}, 반영={}", bcode, centerCode, restored);
        return restored;
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
        bookRepository.insertStockLog(contentId, centerCode, before, after, target < before ? memo : "추가", changedBy);
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
        bookRepository.insertStockLog(contentId, centerCode, contentBefore, contentAfter, target < bcodeBefore ? memo : "추가", changedBy);
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
