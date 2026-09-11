package com.hohoedu.book_clinic.app;

import java.util.Collections;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hohoedu.book_clinic._core.handler.exception.Exception401;
import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic.app._dto.AppReqDTO;
import com.hohoedu.book_clinic.app._dto.AppRespDTO;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * i-with(학부모 Flutter 앱) 화면 조립용 조회 API.
 *
 * 인증은 {@link AppAuthController} 가 만든 세션(studentId)을 그대로 쓴다. studentId 는
 * 항상 세션에서만 가져오고 요청 본문으로는 받지 않는다 — 다른 학생 데이터를 대신 조회하는
 * 경로를 원천 차단하기 위해서다. 로직은 담지 않고 {@link AppRepository} 조회만 위임한다.
 */
@Slf4j
@RestController
@RequestMapping("/app")
@RequiredArgsConstructor
public class AppController {

    private static final String SESSION_STUDENT_ID = "studentId";

    private final AppRepository appRepository;

    /** 책방 메인 화면 — 다음 예약 / 이용권 잔여 / 직전 이용 / 최근 독서기록. */
    @PostMapping("/bookstore/main")
    public ResponseEntity<?> bookstoreMain(HttpServletRequest request) {
        String studentId = requireStudentId(request);
        AppRespDTO.BookstoreMainDTO res = appRepository.selectBookstoreMain(studentId);
        return ResponseEntity.ok(ApiUtils.success(res));
    }

    /**
     * 정독 결과 화면 — 상단 일자 탭 1개 = 응답 1개.
     *
     * recordDate 가 비어 있으면(첫 진입) 가장 최근 정독 일자를 고른다. 일지가 한 건도 없으면
     * 빈 리포트(dates=[] , recordDate=null)를 200 으로 내려준다 — 기록이 없는 건 오류가 아니라
     * 화면의 빈 상태이고, 앱이 에러 분기와 빈 상태 분기를 둘 다 들고 있을 이유가 없다.
     *
     * 쿼리가 여섯 개로 나뉘는 건 카디널리티가 서로 달라서다(요약 1행 / 책 N행 / 뱃지 4행 /
     * 성향 유형별 / 월별). 여기서는 이어붙이기만 하고 계산은 전부 SQL 이 한다.
     */
    @PostMapping("/bookstore/report")
    public ResponseEntity<?> bookstoreReport(@RequestBody(required = false) AppReqDTO.BookstoreReportDTO reqDTO,
                                             HttpServletRequest request) {
        String studentId = requireStudentId(request);

        List<String> dates = appRepository.selectBookstoreReportDates(studentId);
        Collections.reverse(dates); // 최신순 조회 → 탭은 왼쪽이 과거

        String requested = reqDTO == null ? null : trimToNull(reqDTO.getRecordDate());
        // 요청 날짜가 없으면 마지막(가장 최근) 탭. 탭 목록에 없는 날짜가 와도 그대로 조회한다.
        String recordDate = requested != null ? requested
                : (dates.isEmpty() ? null : dates.get(dates.size() - 1));

        AppRespDTO.BookstoreReportDTO res = appRepository.selectBookstoreReportSummary(studentId, recordDate);
        if (res == null) res = new AppRespDTO.BookstoreReportDTO();
        res.setDates(dates);
        res.setRecordDate(recordDate);

        if (recordDate == null) {
            // 정독 기록이 아예 없는 학생 — 조회할 날짜가 없으니 나머지는 빈 리스트로 채운다
            res.setBooks(Collections.emptyList());
            res.setBadges(Collections.emptyList());
            res.setTendencies(Collections.emptyList());
            res.setMonthly(Collections.emptyList());
            return ResponseEntity.ok(ApiUtils.success(res));
        }

        res.setBooks(appRepository.selectBookstoreReportBooks(studentId, recordDate));
        res.setBadges(appRepository.selectBookstoreReportBadges(studentId, recordDate));
        res.setTendencies(appRepository.selectBookstoreReportTendencies(studentId, recordDate));

        List<AppRespDTO.ReportMonthlyDTO> monthly =
                appRepository.selectBookstoreReportMonthly(studentId, recordDate);
        Collections.reverse(monthly); // 그래프는 과거 → 현재 순서로 그린다
        res.setMonthly(monthly);

        return ResponseEntity.ok(ApiUtils.success(res));
    }

    private static String trimToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private String requireStudentId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object studentId = session == null ? null : session.getAttribute(SESSION_STUDENT_ID);
        if (studentId == null) {
            throw new Exception401("로그인이 필요합니다.");
        }
        return studentId.toString();
    }
}
