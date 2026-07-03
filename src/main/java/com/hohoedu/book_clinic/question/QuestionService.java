package com.hohoedu.book_clinic.question;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

import com.hohoedu.book_clinic.book.BookRepository;
import com.hohoedu.book_clinic.book._dto.BookRespDTO;
import com.hohoedu.book_clinic.question._dto.QuestionReqDTO;
import com.hohoedu.book_clinic.question._dto.QuestionRespDTO;

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
        questionRepository.archiveQuestion(reqDTO.getContentId(), reqDTO.getQlevel(), reqDTO.getQnum(), deletedBy);
        questionRepository.deleteQuestion(reqDTO.getContentId(), reqDTO.getQlevel(), reqDTO.getQnum());
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

    /** 도서별 문제 목록 조회 (contentId 필수, qlevel/qtype/state 선택) */
    public List<QuestionRespDTO.QuestionDTO> searchQuestions(Integer contentId, String qlevel, String qtype, String state) {
        return questionRepository.searchQuestions(contentId, qlevel, qtype, state);
    }

    /** 삭제된 문제 목록 조회 */
    public List<QuestionRespDTO.QuestionDelDTO> findDeletedQuestions(Integer contentId) {
        return questionRepository.findDeletedQuestions(contentId);
    }

    /**
     * 엑셀 파일에서 문제를 일괄 등록
     * mode = "check"    : 중복 확인만 (삽입 없음) → 중복 있으면 duplicates 반환
     * mode = "overwrite": 기존 문제 비활성화 후 새 문제 활성 삽입
     * mode = "inactive" : 새 문제를 비활성으로 삽입, 기존 유지
     */
    @Transactional
    public QuestionRespDTO.UploadResultDTO uploadQuestions(MultipartFile file, String mode, String uploadedBy) throws IOException {
        List<QuestionReqDTO.RegisterReqDTO> list = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheet("문제등록");

            for (Row row : sheet) {
                if (row.getRowNum() < 1) continue;
                if (isRowEmpty(row)) continue;

                String selectedTitle = getCellString(row.getCell(0));
                if (selectedTitle == null || selectedTitle.isBlank())
                    throw new IllegalArgumentException((row.getRowNum() + 1) + "행: 도서를 선택하지 않았습니다.");

                Integer contentId = bookRepository.findContentIdByTitle(selectedTitle);
                if (contentId == null)
                    throw new IllegalArgumentException((row.getRowNum() + 1) + "행: 선택한 도서를 찾을 수 없습니다 - " + selectedTitle);

                QuestionReqDTO.RegisterReqDTO dto = new QuestionReqDTO.RegisterReqDTO();
                dto.setContentId(contentId);
                dto.setQnum(formatQnum(getCellString(row.getCell(1))));
                dto.setQlevel(resolveQlevel(getCellString(row.getCell(2)), row.getRowNum() + 1));
                dto.setQ(getCellString(row.getCell(3)));
                dto.setQex(getCellString(row.getCell(4)));
                dto.setE1(getCellString(row.getCell(5)));
                dto.setE2(getCellString(row.getCell(6)));
                dto.setE3(getCellString(row.getCell(7)));
                dto.setE4(getCellString(row.getCell(8)));
                dto.setAns(getCellString(row.getCell(9)));
                dto.setQtype(resolveQtype(getCellString(row.getCell(10)), row.getRowNum() + 1));
                dto.setQexgb(getCellString(row.getCell(11)));
                dto.setState(resolveState(getCellString(row.getCell(12)), row.getRowNum() + 1));
                list.add(dto);
            }
        }

        QuestionRespDTO.UploadResultDTO result = new QuestionRespDTO.UploadResultDTO();
        if (list.isEmpty()) return result;

        // 중복 체크
        List<QuestionRespDTO.DuplicateInfo> duplicates = questionRepository.findExistingQuestions(list);
        if (!duplicates.isEmpty() && "check".equals(mode)) {
            result.setDuplicates(duplicates);
            return result;
        }

        // 중복 키 Set
        Set<String> dupKeys = duplicates.stream()
                .map(d -> d.getContentId() + "|" + d.getQlevel() + "|" + d.getQnum())
                .collect(Collectors.toSet());

        List<QuestionReqDTO.RegisterReqDTO> toInsert = new ArrayList<>();

        for (QuestionReqDTO.RegisterReqDTO dto : list) {
            boolean isDup = dupKeys.contains(dto.getContentId() + "|" + dto.getQlevel() + "|" + dto.getQnum());
            if (isDup) {
                if ("overwrite".equals(mode)) {
                    // 기존 데이터 del 테이블에 보관 후 UPDATE
                    questionRepository.archiveQuestion(dto.getContentId(), dto.getQlevel(), dto.getQnum(), uploadedBy);
                    questionRepository.overwriteQuestion(dto);
                }
                // "skip" mode면 중복은 그냥 건너뜀
            } else {
                toInsert.add(dto);
            }
        }

        if (!toInsert.isEmpty()) {
            questionRepository.registerQuestionBatch(toInsert);
        }

        // 결과 집계: 업데이트된 것 + 새로 삽입된 것
        List<QuestionReqDTO.RegisterReqDTO> processed = "overwrite".equals(mode) ? list : toInsert;
        result.setTotal(processed.size());
        result.setActiveCount((int) processed.stream().filter(d -> !"N".equals(d.getState())).count());
        result.setInactiveCount((int) processed.stream().filter(d -> "N".equals(d.getState())).count());
        return result;
    }

    /**
     * 문제 일괄 등록용 엑셀 템플릿 생성
     * - A열: 도서 선택 드롭다운 (행마다 다른 도서 선택 가능 → 여러 도서 동시 등록)
     * - 2행~: 문제 입력 영역 (도서, 문제번호, 문제, 지문, 보기①~④, 정답, 유형, 지문구분, 상태)
     */
    public Workbook createUploadTemplate() {
        List<BookRespDTO.ContentRespDTO> books = bookRepository.searchContents(null, null, null, null, null, null, null);
        Workbook wb = new XSSFWorkbook();

        Sheet main = wb.createSheet("문제등록");

        // ── 스타일 정의 ──
        CellStyle headerStyle = wb.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font headerFont = wb.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);

        // ── Row 0: 컬럼 헤더 ──
        String[] headers = {"도서", "문제번호", "레벨", "문제", "지문", "보기①", "보기②", "보기③", "보기④", "정답", "유형", "예시문 사용여부", "상태"};
        int[] widths     = {8000, 3000, 3000, 14000, 8000, 4500, 4500, 4500, 4500, 3000, 3500, 3500, 3000};
        Row headerRow = main.createRow(0);
        headerRow.setHeightInPoints(24);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            main.setColumnWidth(i, widths[i]);
        }

        DataValidationHelper dvHelper = main.getDataValidationHelper();

        String[] bookTitles = books.stream()
                .map(BookRespDTO.ContentRespDTO::getOriginalTitle)
                .toArray(String[]::new);

        addDropdown(dvHelper, main, bookTitles,                                                      0,  "목록에서 도서를 선택해주세요.");
        addDropdown(dvHelper, main, new String[]{"기본", "심화"},                                     2,  "목록에서 레벨을 선택해주세요.");
        addDropdown(dvHelper, main, new String[]{"이해", "표현", "논리", "사고", "감정", "어휘", "지식"}, 10, "목록에서 유형을 선택해주세요.");
        addDropdown(dvHelper, main, new String[]{"Y", "N"},                                          11, "Y 또는 N을 선택해주세요.");
        addDropdown(dvHelper, main, new String[]{"활성", "비활성"},                                   12, "목록에서 상태를 선택해주세요.");

        return wb;
    }

    private void addDropdown(DataValidationHelper dvHelper, Sheet sheet, String[] items, int col, String errorMsg) {
        DataValidationConstraint constraint = dvHelper.createExplicitListConstraint(items);
        DataValidation validation = dvHelper.createValidation(constraint, new CellRangeAddressList(1, 1000, col, col));
        validation.setSuppressDropDownArrow(true);
        validation.setShowErrorBox(true);
        validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
        validation.createErrorBox("선택 오류", errorMsg);
        sheet.addValidationData(validation);
    }

    private static final java.util.Map<String, String> QLEVEL_MAP = java.util.Map.of(
            "기본", "01", "심화", "02"
    );

    private static final java.util.Map<String, String> QTYPE_MAP = java.util.Map.of(
            "이해", "01", "표현", "02", "논리", "03", "사고", "04",
            "감정", "05", "어휘", "06", "지식", "07"
    );

    private String formatQnum(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        try {
            return String.format("%02d", Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    private String resolveState(String value, int rowNum) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(rowNum + "행: 상태를 선택하지 않았습니다.");
        return switch (value) {
            case "활성" -> "Y";
            case "비활성" -> "N";
            default -> throw new IllegalArgumentException(rowNum + "행: 알 수 없는 상태 값 - " + value);
        };
    }

    private String resolveQlevel(String value, int rowNum) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(rowNum + "행: 레벨을 선택하지 않았습니다.");
        String code = QLEVEL_MAP.get(value);
        if (code == null)
            throw new IllegalArgumentException(rowNum + "행: 알 수 없는 레벨 값 - " + value);
        return code;
    }

    private String resolveQtype(String value, int rowNum) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(rowNum + "행: 유형을 선택하지 않았습니다.");
        String code = QTYPE_MAP.get(value);
        if (code == null)
            throw new IllegalArgumentException(rowNum + "행: 알 수 없는 유형 값 - " + value);
        return code;
    }

    /** 셀 값을 문자열로 변환 (숫자 셀도 처리, 줄바꿈 보존) */
    private String getCellString(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC) {
            return String.valueOf((long) cell.getNumericCellValue());
        }
        return cell.getStringCellValue()
                .replace("\r\n", "\n")
                .replace("\r", "\n");
    }

    /** 행이 비어 있는지 확인 */
    private boolean isRowEmpty(Row row) {
        Cell first = row.getCell(0);
        return first == null || first.getCellType() == CellType.BLANK;
    }

}
