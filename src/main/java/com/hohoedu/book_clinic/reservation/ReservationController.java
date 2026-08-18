package com.hohoedu.book_clinic.reservation;

import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hohoedu.book_clinic._core.handler.exception.Exception401;
import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic.reservation._dto.ReservationReqDTO;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * 학생 앱(Flutter, payment_temp) 예약 API (2026-08-18).
 *
 * 인증은 {@code student.AppAuthController}가 만든 세션(studentId)을 그대로 쓴다. studentId는
 * 항상 세션에서만 가져오고 요청 본문으로는 받지 않는다 — 클라이언트가 다른 학생 id를 보내
 * 대신 예약하게 되는 경로를 원천 차단하기 위해서다. 센터 직원 대리 예약은
 * {@link ReservationAdminController}가 같은 {@link ReservationService}를 studentId만 다르게 불러 처리한다.
 */
@RestController
@RequestMapping("/app/reservation")
@RequiredArgsConstructor
public class ReservationController {

    private static final String SESSION_STUDENT_ID = "studentId";

    private final ReservationService reservationService;

    /** 예약 가능한 슬롯 목록. fromDate/toDate 생략 시 오늘부터 4주 */
    @GetMapping("/slots")
    public ResponseEntity<?> slots(@RequestParam(value = "fromDate", required = false) String fromDate,
                                   @RequestParam(value = "toDate", required = false) String toDate,
                                   HttpServletRequest request) {
        String studentId = requireStudentId(request);
        return ResponseEntity.ok(ApiUtils.success(
                reservationService.findOpenSlots(studentId, parseDate(fromDate), parseDate(toDate))));
    }

    /** 내 예약 목록(RESERVED, 오늘 이후) */
    @GetMapping("/my")
    public ResponseEntity<?> my(HttpServletRequest request) {
        String studentId = requireStudentId(request);
        return ResponseEntity.ok(ApiUtils.success(reservationService.findMyReservations(studentId)));
    }

    /** 단건 예약 */
    @PostMapping
    public ResponseEntity<?> reserve(@RequestBody ReservationReqDTO.ReserveReqDTO reqDTO, HttpServletRequest request) {
        String studentId = requireStudentId(request);
        return ResponseEntity.ok(ApiUtils.success(
                reservationService.reserve(studentId, reqDTO.getSlotInstanceId())));
    }

    /** 예약 취소 */
    @PostMapping("/cancel")
    public ResponseEntity<?> cancel(@RequestBody ReservationReqDTO.CancelReqDTO reqDTO, HttpServletRequest request) {
        String studentId = requireStudentId(request);
        reservationService.cancel(studentId, reqDTO.getReservationId(), reqDTO.getReason());
        return ResponseEntity.ok(ApiUtils.success("예약이 취소되었습니다."));
    }

    /** 4주 일괄 신청 미리보기 — 요일(1=월~7=일)·회차로 가장 가까운 4개 날짜의 슬롯 상태를 알려준다 */
    @GetMapping("/batch-preview")
    public ResponseEntity<?> batchPreview(@RequestParam("dayOfWeek") Integer dayOfWeek,
                                          @RequestParam("seq") Integer seq,
                                          HttpServletRequest request) {
        String studentId = requireStudentId(request);
        return ResponseEntity.ok(ApiUtils.success(reservationService.previewBatch(studentId, dayOfWeek, seq)));
    }

    /** 4주 일괄 확정 — 미리보기에서 확인한 슬롯 id 목록을 그대로 보낸다 */
    @PostMapping("/batch")
    public ResponseEntity<?> batchReserve(@RequestBody ReservationReqDTO.BatchReserveReqDTO reqDTO,
                                          HttpServletRequest request) {
        String studentId = requireStudentId(request);
        return ResponseEntity.ok(ApiUtils.success(
                reservationService.reserveBatch(studentId, reqDTO.getSlotInstanceIds())));
    }

    private LocalDate parseDate(String value) {
        return (value == null || value.isBlank()) ? null : LocalDate.parse(value);
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
