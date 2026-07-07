package com.hohoedu.book_clinic.priority;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hohoedu.book_clinic._core.handler.exception.Exception404;
import com.hohoedu.book_clinic.priority._dto.PriorityReqDTO;
import com.hohoedu.book_clinic.priority._dto.PriorityRespDTO;

import lombok.RequiredArgsConstructor;

/**
 * 권장도서 순위 비즈니스 로직 서비스
 * - 학년탭 단위로 초안(draft)을 여러 건 저장할 수 있고, 그중 하나만 "적용(is_active='Y')" 상태를 가진다.
 * - 스케줄러 없이, 화면 조회 시 "연도=올해"로 필터링하는 것만으로 연초 자동 반영을 처리한다 (연도 자체를 바꾸는 것은 화면/운영 정책의 몫).
 */
@Service
@RequiredArgsConstructor
public class PriorityService {

    private static final String[] EXCEL_HEADERS = {
            "NO", "학년", "도서분류", "도서명", "장르", "난이도", "연계 교과", "추천기관", "수상명"
    };

    // 학년탭 순서 고정 (초1 ~ 중등) — 데이터가 없는 학년도 빈 시트로 항상 표시
    private static final Map<String, String> EXCEL_SCHOOLYEAR_NAMES = new LinkedHashMap<>();
    static {
        EXCEL_SCHOOLYEAR_NAMES.put("01", "초1");
        EXCEL_SCHOOLYEAR_NAMES.put("02", "초2");
        EXCEL_SCHOOLYEAR_NAMES.put("03", "초3");
        EXCEL_SCHOOLYEAR_NAMES.put("04", "초4");
        EXCEL_SCHOOLYEAR_NAMES.put("05", "초5");
        EXCEL_SCHOOLYEAR_NAMES.put("06", "초6");
        EXCEL_SCHOOLYEAR_NAMES.put("07", "중등");
    }

    private final PriorityRepository priorityRepository;

    /**
     * 순위 초안 저장
     * - applyNow=false("내년 등록"): 여러 초안을 예약 저장할 수 있고, 해당 연도+학년의 첫 저장이면 자동으로 적용(활성) 상태가 된다
     * - applyNow=true("이번년도 즉시 적용"): 예약 개념이 아니라 지금 적용 중인 것을 바로 교체하는 것이므로,
     *   그 연도+학년에 기존에 있던 초안은 전부 del로 이관하고 새로 저장한 것 하나만 남긴다 (예약 목록에도 노출되지 않음)
     */
    @Transactional
    public Integer saveDraft(PriorityReqDTO.SaveDraftReqDTO reqDTO, String createdBy) {
        if (Boolean.TRUE.equals(reqDTO.getApplyNow())) {
            for (PriorityRespDTO.DraftDTO existing : priorityRepository.findDrafts(reqDTO.getYear(), reqDTO.getSchoolYear())) {
                deleteDraft(existing.getDraftId(), createdBy);
            }
            priorityRepository.insertDraft(reqDTO, "Y", createdBy);
        } else {
            boolean isFirstDraft = priorityRepository.countDrafts(reqDTO.getYear(), reqDTO.getSchoolYear()) == 0;
            priorityRepository.insertDraft(reqDTO, isFirstDraft ? "Y" : "N", createdBy);
        }

        List<PriorityReqDTO.RankedItem> items = new ArrayList<>();
        List<Integer> contentIds = reqDTO.getContentIds();
        for (int i = 0; i < contentIds.size(); i++) {
            items.add(new PriorityReqDTO.RankedItem(contentIds.get(i), i + 1));
        }
        priorityRepository.insertPriorityRows(reqDTO.getDraftId(), items);

        return reqDTO.getDraftId();
    }

    /** 현재 적용 중인 초안 기준으로 학년 전체 도서 + 순위 조회 */
    public List<PriorityRespDTO.RankedBookDTO> findRankedBooks(String year, String schoolYear) {
        return priorityRepository.findRankedBooks(year, schoolYear);
    }

    /** 해당 연도+학년의 저장된 초안 목록 조회 */
    public List<PriorityRespDTO.DraftDTO> findDrafts(String year, String schoolYear) {
        return priorityRepository.findDrafts(year, schoolYear);
    }

    /** 특정 초안의 순위 미리보기 */
    public List<PriorityRespDTO.RankedBookDTO> findBooksByDraftId(Integer draftId) {
        return priorityRepository.findBooksByDraftId(draftId);
    }

    /** 초안 적용(선택) — 같은 연도+학년의 다른 초안은 전부 비활성화하고 선택한 것만 활성화 */
    @Transactional
    public void activateDraft(PriorityReqDTO.ActivateDraftReqDTO reqDTO) {
        PriorityRespDTO.DraftDTO draft = priorityRepository.findDraftById(reqDTO.getDraftId());
        if (draft == null) throw new Exception404("해당 초안을 찾을 수 없습니다: " + reqDTO.getDraftId());

        priorityRepository.deactivateDrafts(draft.getYear(), draft.getSchoolyear());
        priorityRepository.activateDraftById(reqDTO.getDraftId());
    }

    /** 초안 삭제 — _del로 그대로 이관만 하고 복구는 지원하지 않는다 */
    @Transactional
    public void deleteDraft(Integer draftId, String deletedBy) {
        PriorityRespDTO.DraftDTO draft = priorityRepository.findDraftById(draftId);
        if (draft == null) throw new Exception404("해당 초안을 찾을 수 없습니다: " + draftId);

        priorityRepository.insertDraftDel(draftId, deletedBy);
        priorityRepository.insertPriorityDelRows(draftId);
        priorityRepository.deletePriorityRowsByDraftId(draftId);
        priorityRepository.deleteDraftById(draftId);
    }

    /** 권장도서 순위 엑셀 다운로드 — 학년(초1~중등) 7개 시트, 해당 연도에 적용 중인 순위를 그대로 담는다 */
    public byte[] buildExcelWorkbook(String year) {
        Map<String, List<PriorityRespDTO.ExcelRowDTO>> bySchoolyear = priorityRepository.findExcelRows(year).stream()
                .collect(Collectors.groupingBy(PriorityRespDTO.ExcelRowDTO::getSchoolyear, LinkedHashMap::new, Collectors.toList()));

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);

            EXCEL_SCHOOLYEAR_NAMES.forEach((code, name) -> {
                Sheet sheet = workbook.createSheet(name);
                writeHeaderRow(sheet, headerStyle);
                writeDataRows(sheet, bySchoolyear.getOrDefault(code, List.of()));
                applyColumnWidths(sheet);
            });

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("엑셀 생성 중 오류가 발생했습니다.", e);
        }
    }

    private void writeHeaderRow(Sheet sheet, CellStyle headerStyle) {
        Row headerRow = sheet.createRow(0);
        for (int c = 0; c < EXCEL_HEADERS.length; c++) {
            Cell cell = headerRow.createCell(c);
            cell.setCellValue(EXCEL_HEADERS[c]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void writeDataRows(Sheet sheet, List<PriorityRespDTO.ExcelRowDTO> rows) {
        int rowIdx = 1;
        int no = 1;
        for (PriorityRespDTO.ExcelRowDTO r : rows) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(no++);
            row.createCell(1).setCellValue(nvl(r.getSchoolyearName()));
            row.createCell(2).setCellValue(nvl(r.getContentTypeName()));
            row.createCell(3).setCellValue(nvl(r.getOriginalTitle()));
            row.createCell(4).setCellValue(nvl(r.getGenreName()));
            row.createCell(5).setCellValue(nvl(r.getDifficulty()));
            row.createCell(6).setCellValue(nvl(r.getCurriculumName()));
            row.createCell(7).setCellValue(nvl(r.getRecommendOrgName()));
            row.createCell(8).setCellValue(nvl(r.getAwardName()));
        }
    }

    private void applyColumnWidths(Sheet sheet) {
        int[] widths = {6, 8, 12, 30, 12, 8, 16, 16, 20};
        for (int c = 0; c < widths.length; c++) {
            sheet.setColumnWidth(c, widths[c] * 256);
        }
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

}
