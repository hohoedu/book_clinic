package com.hohoedu.book_clinic.app;

import java.time.YearMonth;
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
import com.hohoedu.book_clinic.reservation.ReservationService;

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
    private final AppReportService appReportService;
    private final ReservationService reservationService;

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
     * recordDate 가 비어 있으면(첫 진입) 가장 최근 정독 일자를 고른다. 조립은
     * {@link AppReportService} 가 하고, 여기서는 세션의 studentId 만 실어 보낸다.
     *
     * sentOnly=true — 직원이 발송하지 않은 일지는 앱에 보이지 않는다(2026-09-23). 발송 전
     * 결과가 학부모에게 먼저 보이면 직원이 검토·보정할 틈이 없어진다.
     */
    @PostMapping("/bookstore/report")
    public ResponseEntity<?> bookstoreReport(@RequestBody(required = false) AppReqDTO.BookstoreReportDTO reqDTO,
                                             HttpServletRequest request) {
        String studentId = requireStudentId(request);
        String recordDate = reqDTO == null ? null : reqDTO.getRecordDate();
        return ResponseEntity.ok(ApiUtils.success(
                appReportService.getReport(studentId, recordDate, true)));
    }

    /**
     * 달력 화면(/calendar) 데이터 — 그 달 한 달치 회차 목록.
     *
     * 예약하기 화면과 같은 조회({@link ReservationService#findOpenSlots})를 기간만 그 달로 바꿔
     * 그대로 쓴다. 날짜별 색(예약완료/가능/마감)은 화면이 회차들을 모아 판단한다 — 앱 예약 달력
     * (BookstoreReservationData.dayStatuses)이 쓰는 규칙과 같아야 두 화면이 어긋나지 않는다.
     *
     * 응답이 회차 단위인 건 일부러다. 여기서 날짜별로 미리 접어 내리면 같은 데이터에 대한 판단
     * 기준이 서버와 앱 두 곳으로 갈라진다.
     */
    @PostMapping("/calendar")
    public ResponseEntity<?> calendar(@RequestBody AppReqDTO.CalendarDTO reqDTO, HttpServletRequest request) {
        String studentId = requireStudentId(request);
        YearMonth ym = YearMonth.of(Integer.parseInt(reqDTO.getYear()), Integer.parseInt(reqDTO.getMonth()));
        return ResponseEntity.ok(ApiUtils.success(
                reservationService.findOpenSlots(studentId, ym.atDay(1), ym.atEndOfMonth())));
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
