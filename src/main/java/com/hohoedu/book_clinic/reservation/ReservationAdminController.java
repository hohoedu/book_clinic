package com.hohoedu.book_clinic.reservation;

import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hohoedu.book_clinic._core.auth.CustomUserDetails;
import com.hohoedu.book_clinic._core.handler.exception.Exception401;
import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic.reservation._dto.ReservationReqDTO;

import lombok.RequiredArgsConstructor;

/**
 * 센터 직원 대리 예약 API (2026-08-18) — old erp_bookstore_clinic_reservation 기반이던
 * 기존 컨트롤러를 신규 스키마로 대체한다.
 *
 * 학생 앱({@link ReservationController})과 완전히 같은 {@link ReservationService}를 부른다 —
 * 직원이라고 정원 체크를 건너뛰는 경로를 별도로 두지 않는다(우선권 없음). 다른 점은 대상
 * 학생을 세션이 아니라 요청 본문(studentId)으로 직접 지정한다는 것뿐이다.
 *
 * [프론트엔드 주의] old 화면(reservation.html/reservation.js)은 (studentId, date, timeSlot)로
 * 등록을 호출했지만, 신규 스키마는 정원을 가진 slotInstanceId 단위로만 예약을 받는다. 화면이
 * 먼저 /slots로 그 날짜의 슬롯 목록(slotInstanceId 포함)을 조회한 뒤 골라서 등록하도록
 * 별도로 손봐야 한다 — 이 커밋은 백엔드 계약만 새 스키마로 맞춘 상태다.
 */
@RestController
@RequestMapping("/admin/monitor/reservation")
@RequiredArgsConstructor
public class ReservationAdminController {

    private final ReservationService reservationService;

    /** 특정 날짜의 예약 목록 */
    @GetMapping("/list")
    public ResponseEntity<?> list(@RequestParam("date") String date,
                                  @AuthenticationPrincipal CustomUserDetails userDetails) {
        String centerCode = requireCenterCode(userDetails);
        return ResponseEntity.ok(ApiUtils.success(
                reservationService.findReservationsByDate(centerCode, LocalDate.parse(date))));
    }

    /** 특정 날짜의 회차별 정원 요약(회차 카드 표시용) */
    @GetMapping("/summary")
    public ResponseEntity<?> summary(@RequestParam("date") String date,
                                     @AuthenticationPrincipal CustomUserDetails userDetails) {
        String centerCode = requireCenterCode(userDetails);
        return ResponseEntity.ok(ApiUtils.success(
                reservationService.findSlotSummary(centerCode, LocalDate.parse(date))));
    }

    /**
     * 예약 가능한 슬롯 목록(대리 등록 대상 선택용). studentId는 아직 학생을 고르지 않은
     * "등록" 모드에선 빈 값으로 넘어온다 — 그래서 센터는 studentId가 아니라 로그인한 직원
     * 기준으로 정한다(학생 앱용 findOpenSlots를 그대로 썼다가 빈 studentId에 401이 났던 지점).
     */
    @GetMapping("/slots")
    public ResponseEntity<?> slots(@RequestParam(value = "studentId", required = false) String studentId,
                                   @RequestParam(value = "fromDate", required = false) String fromDate,
                                   @RequestParam(value = "toDate", required = false) String toDate,
                                   @AuthenticationPrincipal CustomUserDetails userDetails) {
        String centerCode = requireCenterCode(userDetails);
        LocalDate from = (fromDate == null || fromDate.isBlank()) ? null : LocalDate.parse(fromDate);
        LocalDate to = (toDate == null || toDate.isBlank()) ? null : LocalDate.parse(toDate);
        return ResponseEntity.ok(ApiUtils.success(reservationService.findOpenSlotsByCenter(centerCode, from, to, studentId)));
    }

    /** 이름/appId로 학생 검색 — 로그인한 직원의 센터 소속 학생만 */
    @GetMapping("/students")
    public ResponseEntity<?> searchStudents(@RequestParam(value = "keyword", required = false) String keyword,
                                            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String centerCode = requireCenterCode(userDetails);
        return ResponseEntity.ok(ApiUtils.success(
                reservationService.searchStudents(centerCode, keyword == null ? "" : keyword)));
    }

    /** 예약 등록(대리) — changed_by_role을 ADMIN으로 남겨야 예약 현황 화면에 "센터 예약"으로 뜬다 */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody ReservationReqDTO.AdminReserveReqDTO reqDTO,
                                      @AuthenticationPrincipal CustomUserDetails userDetails) {
        requireCenterCode(userDetails);
        return ResponseEntity.ok(ApiUtils.success(
                reservationService.reserveByAdmin(reqDTO.getStudentId(), reqDTO.getSlotInstanceId(), userDetails.getLoginUser().getUserId())));
    }

    /** 예약 취소(대리) */
    @PostMapping("/cancel")
    public ResponseEntity<?> cancel(@RequestBody ReservationReqDTO.AdminCancelReqDTO reqDTO,
                                    @AuthenticationPrincipal CustomUserDetails userDetails) {
        requireCenterCode(userDetails);
        reservationService.cancelByAdmin(reqDTO.getStudentId(), reqDTO.getReservationId(), reqDTO.getReason(), userDetails.getLoginUser().getUserId());
        return ResponseEntity.ok(ApiUtils.success("취소되었습니다."));
    }

    private String requireCenterCode(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getLoginUser().getCenterCode() == null) {
            throw new Exception401("로그인이 필요합니다.");
        }
        return userDetails.getLoginUser().getCenterCode();
    }

}
