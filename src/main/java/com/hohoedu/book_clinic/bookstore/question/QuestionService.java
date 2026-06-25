package com.hohoedu.book_clinic.bookstore.question;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.hohoedu.book_clinic.bookstore.book.BookRepository;
import com.hohoedu.book_clinic.bookstore.book._dto.BookRespDTO;
import com.hohoedu.book_clinic.bookstore.question._dto.QuestionReqDTO;
import com.hohoedu.book_clinic.bookstore.question._dto.QuestionRespDTO;

import lombok.RequiredArgsConstructor;

/**
 * 문제 비즈니스 로직 서비스
 * erp_bookstore_itempool / erp_bookstore_itempool_del 관리
 * 마스터 도서 삭제(sp_delete_book) 시 itempool도 일괄 처리되므로 개별 삭제는 단건 문제 삭제에만 사용
 */
@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final BookRepository bookRepository;

    /** 문제 등록 */
    public void registerQuestion(QuestionReqDTO.RegisterReqDTO reqDTO) {
        questionRepository.registerQuestion(reqDTO);
    }

    /** 문제 수정 */
    public void updateQuestion(QuestionReqDTO.UpdateReqDTO reqDTO) {
        questionRepository.updateQuestion(reqDTO);
    }

    /**
     * 문제 삭제
     * itempool_del로 이관 후 itempool에서 삭제
     */
    @Transactional
    public void deleteQuestion(QuestionReqDTO.DeleteReqDTO reqDTO, String deletedBy) {
        questionRepository.archiveQuestion(reqDTO.getContentId(), reqDTO.getQnum(), deletedBy);
        questionRepository.deleteQuestion(reqDTO.getContentId(), reqDTO.getQnum());
    }

    /**
     * 문제 복구
     * itempool_del에서 itempool로 복원 후 del 레코드 제거
     */
    @Transactional
    public void restoreQuestion(QuestionReqDTO.RestoreReqDTO reqDTO) {
        questionRepository.restoreQuestionFromDel(reqDTO.getDelId());
        questionRepository.deleteQuestionDel(reqDTO.getDelId());
    }

    /** 도서별 문제 목록 조회 (contentId 필수, qtype/state 선택) */
    public List<QuestionRespDTO.QuestionDTO> searchQuestions(Integer contentId, String qtype, String state) {
        return questionRepository.searchQuestions(contentId, qtype, state);
    }

    /** 삭제된 문제 목록 조회 */
    public List<QuestionRespDTO.QuestionDelDTO> findDeletedQuestions(Integer contentId) {
        return questionRepository.findDeletedQuestions(contentId);
    }

    /**
     * 엑셀 파일에서 문제를 일괄 등록
     * 엑셀 B2 셀의 도서 선택 드롭다운 값으로 contentId를 결정
     * 데이터는 5행(인덱스 4)부터 시작: 문제번호 | 문제 | 지문 | 보기① ~ ④ | 정답 | 유형 | 지문구분 | 상태
     */
    @Transactional
    public int uploadQuestions(MultipartFile file) throws IOException {
        List<QuestionReqDTO.RegisterReqDTO> list = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            // B2 셀(row=1, col=1)에서 선택한 도서 제목 읽기
            Row bookRow = sheet.getRow(1);
            if (bookRow == null) throw new IllegalArgumentException("엑셀 형식이 올바르지 않습니다. 양식을 다시 다운로드해주세요.");
            String selectedTitle = getCellString(bookRow.getCell(1));
            if (selectedTitle == null || selectedTitle.isBlank())
                throw new IllegalArgumentException("도서를 선택하지 않았습니다. B2 셀의 드롭다운에서 도서를 선택해주세요.");

            Integer contentId = bookRepository.findContentIdByTitle(selectedTitle);
            if (contentId == null)
                throw new IllegalArgumentException("선택한 도서를 찾을 수 없습니다: " + selectedTitle);

            // 5행(인덱스 4)부터 문제 데이터 파싱
            for (Row row : sheet) {
                if (row.getRowNum() < 4) continue;
                if (isRowEmpty(row)) continue;

                QuestionReqDTO.RegisterReqDTO dto = new QuestionReqDTO.RegisterReqDTO();
                dto.setContentId(contentId);
                dto.setQnum(getCellString(row.getCell(0)));
                dto.setQ(getCellString(row.getCell(1)));
                dto.setQex(getCellString(row.getCell(2)));
                dto.setE1(getCellString(row.getCell(3)));
                dto.setE2(getCellString(row.getCell(4)));
                dto.setE3(getCellString(row.getCell(5)));
                dto.setE4(getCellString(row.getCell(6)));
                dto.setAns(getCellString(row.getCell(7)));
                dto.setQtype(getCellString(row.getCell(8)));
                dto.setQexgb(getCellString(row.getCell(9)));
                dto.setState(getCellString(row.getCell(10)));
                list.add(dto);
            }
        }

        if (list.isEmpty()) return 0;
        questionRepository.registerQuestionBatch(list);
        return list.size();
    }

    /**
     * 문제 일괄 등록용 엑셀 템플릿 생성
     * - B2 셀: DB에서 가져온 도서 목록으로 드롭다운 구성
     * - 5행~: 문제 입력 영역 (문제번호, 문제, 지문, 보기①~④, 정답, 유형, 지문구분, 상태)
     */
    public Workbook createUploadTemplate(List<BookRespDTO.ContentRespDTO> books) {
        Workbook wb = new XSSFWorkbook();
        Sheet main = wb.createSheet("문제등록");

        // ── 스타일 정의 ──
        CellStyle titleStyle = wb.createCellStyle();
        titleStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font titleFont = wb.createFont();
        titleFont.setBold(true);
        titleFont.setColor(IndexedColors.WHITE.getIndex());
        titleFont.setFontHeightInPoints((short) 13);
        titleStyle.setFont(titleFont);

        CellStyle labelStyle = wb.createCellStyle();
        labelStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        labelStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font labelFont = wb.createFont();
        labelFont.setBold(true);
        labelStyle.setFont(labelFont);

        CellStyle headerStyle = wb.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font headerFont = wb.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);

        // ── Row 0: 제목 ──
        Row titleRow = main.createRow(0);
        titleRow.setHeightInPoints(28);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("문제 일괄 등록 양식");
        titleCell.setCellStyle(titleStyle);

        // ── Row 1: 도서 선택 드롭다운 ──
        Row bookRow = main.createRow(1);
        bookRow.setHeightInPoints(24);
        Cell labelCell = bookRow.createCell(0);
        labelCell.setCellValue("도서 선택");
        labelCell.setCellStyle(labelStyle);
        // B2(row=1, col=1)에 드롭다운 - 아래에서 DataValidation으로 설정

        // ── Row 2: 공백 구분선 ──
        main.createRow(2);

        // ── Row 3: 문제 컬럼 헤더 ──
        String[] headers = {"문제번호", "문제", "지문", "보기①", "보기②", "보기③", "보기④", "정답", "유형", "지문구분", "상태"};
        int[] widths     = {3000, 14000, 8000, 4500, 4500, 4500, 4500, 3000, 3500, 3500, 3000};
        Row headerRow = main.createRow(3);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            main.setColumnWidth(i, widths[i]);
        }

        // ── 도서 목록 히든 시트 생성 ──
        Sheet bookSheet = wb.createSheet("도서목록");
        wb.setSheetHidden(wb.getSheetIndex("도서목록"), true);
        for (int i = 0; i < books.size(); i++) {
            Row row = bookSheet.createRow(i);
            row.createCell(0).setCellValue(books.get(i).getOriginalTitle());
        }

        // ── Named Range: BookList → 도서목록!$A$1:$A$N ──
        org.apache.poi.ss.usermodel.Name namedRange = wb.createName();
        namedRange.setNameName("BookList");
        namedRange.setRefersToFormula("'도서목록'!$A$1:$A$" + books.size());

        // ── DataValidation: B2 셀에 드롭다운 적용 ──
        DataValidationHelper dvHelper = main.getDataValidationHelper();
        DataValidationConstraint constraint = dvHelper.createFormulaListConstraint("BookList");
        CellRangeAddressList rangeList = new CellRangeAddressList(1, 1, 1, 1);
        DataValidation validation = dvHelper.createValidation(constraint, rangeList);
        validation.setSuppressDropDownArrow(false);
        validation.setShowErrorBox(true);
        validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
        validation.createErrorBox("선택 오류", "목록에서 도서를 선택해주세요.");
        main.addValidationData(validation);

        return wb;
    }

    /** 셀 값을 문자열로 변환 (숫자 셀도 처리) */
    private String getCellString(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC) {
            return String.valueOf((long) cell.getNumericCellValue());
        }
        return cell.getStringCellValue().trim();
    }

    /** 행이 비어 있는지 확인 */
    private boolean isRowEmpty(Row row) {
        Cell first = row.getCell(0);
        return first == null || first.getCellType() == CellType.BLANK;
    }

}
