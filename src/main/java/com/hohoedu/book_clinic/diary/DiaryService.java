package com.hohoedu.book_clinic.diary;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic.diary._dto.DiaryReqDTO;
import com.hohoedu.book_clinic.diary._dto.DiaryRespDTO;
import com.hohoedu.book_clinic.monitor.MonitorRepository;
import com.hohoedu.book_clinic.monitor.MonitorService;

import lombok.RequiredArgsConstructor;

/**
 * 독서일지 조회 (2026-07-30) — 예약 내역과 일지를 한 화면에서 맞물려 보여준다.
 * 목록의 기본 골격은 예약(그날 올 예정인 학생)이고, 세션/일지 데이터가 있으면 그 값으로 덮는다.
 * 덮어쓰기 자체는 SQL의 COALESCE가 처리하고(DiaryMapper), 여기서는 학생별 도서 묶기와
 * 태도 코드 문자열 분해, 출결 상태 파생만 맡는다.
 */
@Service
@RequiredArgsConstructor
public class DiaryService {

    private final DiaryRepository diaryRepository;
    private final MonitorRepository monitorRepository;
    // 저장은 실시간 모니터링과 같은 경로를 타야 한다(같은 테이블 + Firestore 미러링)
    private final MonitorService monitorService;

    public DiaryRespDTO.DiaryViewRespDTO getDiaryView(LocalDate date, String centerCode,
                                                      String timeSlot, String keyword) {
        List<DiaryRespDTO.RowDTO> rows = diaryRepository.findDiaryRows(date, centerCode, timeSlot, keyword);

        Map<String, List<DiaryRespDTO.BookDTO>> booksByStudent = diaryRepository.findDiaryBooks(date, centerCode)
                .stream()
                .collect(Collectors.groupingBy(DiaryRespDTO.BookDTO::getStudentId));

        rows.forEach(row -> fillDerivedFields(row, booksByStudent));

        DiaryRespDTO.DiaryViewRespDTO resp = new DiaryRespDTO.DiaryViewRespDTO();
        resp.setRows(rows);
        resp.setAttitudeCodeOptions(monitorRepository.findActiveAttitudeCodes());
        return resp;
    }

    /**
     * 독서태도·기타 전달사항 일괄 저장 — 상단 "저장" 버튼. 화면이 바뀐 행만 보내온다.
     * 태도는 실시간 모니터링과 같은 테이블/같은 방식(전량 삭제 후 재삽입 — 체크 해제까지 반영)이고,
     * 저장 후 Firestore 미러링도 모니터링과 같은 경로를 타서 그쪽 화면에 바로 반영된다.
     * 도움 필요(help_needed)는 여기서 저장하지 않는다 — 모니터링에서만 켜고 끄는 학생 상태값이라
     * 이 화면의 저장이 그날 스냅샷을 덮어쓰면 안 된다(upsertDiaryNote가 그 컬럼을 건드리지 않는다).
     * 같은 일지를 두 화면에서 동시에 고치면 나중 저장이 이긴다(행 잠금은 두지 않는다).
     */
    @Transactional
    public int saveDiaries(DiaryReqDTO.BulkSaveReqDTO req, String centerCode, String staffName) {
        for (DiaryReqDTO.SaveItemDTO item : req.getItems()) {
            String sessionCenter = diaryRepository.findSessionCenterCode(item.getSessionId());
            if (sessionCenter == null) {
                throw new Exception400("입실 기록이 없어 저장할 수 없습니다.");
            }
            if (!sessionCenter.equals(centerCode)) {
                throw new Exception400("다른 센터의 기록은 수정할 수 없습니다.");
            }

            diaryRepository.upsertDiaryNote(item.getSessionId(), item.getMemo(), staffName);

            Integer diaryKey = monitorRepository.findDiaryKeyBySessionId(item.getSessionId());
            if (diaryKey != null) {
                monitorRepository.deleteDiaryAttitudes(diaryKey);
                List<String> codes = item.getAttitudeCodes();
                if (codes != null && !codes.isEmpty()) {
                    monitorRepository.insertDiaryAttitudes(diaryKey, item.getStudentId(), codes);
                }
            }
            monitorService.syncSession(item.getSessionId());
        }
        return req.getItems().size();
    }

    /**
     * 등/하원 시각 보정 — 일지 헤더의 in_time/out_time만 고친다(세션의 입·퇴실 원본은 그대로 둔다).
     * 미입실(세션 없음) 행은 붙일 일지 헤더가 없어서 화면에서 애초에 입력을 막아두지만,
     * 요청이 들어와도 여기서 막는다(erp_bookstore_diary.session_id가 NOT NULL이라 저장 자체가 불가).
     */
    @Transactional
    public DiaryRespDTO.TimeRespDTO saveDiaryTime(DiaryReqDTO.TimeReqDTO req, String centerCode) {
        String sessionCenter = diaryRepository.findSessionCenterCode(req.getSessionId());
        if (sessionCenter == null) {
            throw new Exception400("입실 기록이 없어 등·하원 시각을 저장할 수 없습니다.");
        }
        if (!sessionCenter.equals(centerCode)) {
            throw new Exception400("다른 센터의 기록은 수정할 수 없습니다.");
        }

        String inTime = blankToNull(req.getInTime());
        String outTime = blankToNull(req.getOutTime());
        if (inTime == null && outTime != null) {
            throw new Exception400("등원 시각 없이 하원 시각만 저장할 수 없습니다.");
        }
        // "HH:mm"은 자리수가 고정이라 문자열 비교로도 시각 순서가 그대로 나온다
        if (inTime != null && outTime != null && outTime.compareTo(inTime) < 0) {
            throw new Exception400("하원 시각이 등원 시각보다 앞설 수 없습니다.");
        }

        diaryRepository.upsertDiaryTime(req.getSessionId(), inTime, outTime);
        return diaryRepository.findDiaryTime(req.getSessionId());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void fillDerivedFields(DiaryRespDTO.RowDTO row,
                                   Map<String, List<DiaryRespDTO.BookDTO>> booksByStudent) {
        row.setAttendStatus(deriveAttendStatus(row));
        row.setAttitudeCodes(splitAttitudeCodes(row.getAttitudeCodesRaw()));
        // 미입실(세션 없음)이면 읽은 책도 없다 — 같은 날 다른 타임에 이미 입실했던 학생의 책이
        // 미입실 행에 딸려 붙는 것을 막기 위해 세션이 있는 행에만 붙인다.
        row.setBooks(row.getSessionId() == null
                ? List.of()
                : booksByStudent.getOrDefault(row.getStudentId(), List.of()));
    }

    /** 세션이 없으면 미입실, 있으면 세션 상태(ENTERED/EXITED)를 그대로 쓴다 */
    private String deriveAttendStatus(DiaryRespDTO.RowDTO row) {
        if (row.getSessionId() == null) return "NOT_ENTERED";
        return "EXITED".equals(row.getSessionStatus()) ? "EXITED" : "ENTERED";
    }

    private List<String> splitAttitudeCodes(String raw) {
        if (raw == null || raw.isBlank()) return new ArrayList<>();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .toList();
    }

}
