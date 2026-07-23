package com.hohoedu.book_clinic.reservation;

import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic.reservation._dto.ReservationReqDTO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 클리닉 예약 등록 관리자 API — 실시간 모니터링(2026-07-15)이 읽는 예약 마스터 CRUD (2026-07-23) */
@RestController
@RequestMapping("/admin/monitor/reservation")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    /** 특정 날짜의 예약 목록 */
    @GetMapping("/list")
    public ResponseEntity<?> list(@RequestParam("date") String date) {
        return ResponseEntity.ok(ApiUtils.success(reservationService.findByDate(LocalDate.parse(date))));
    }

    /** 이름/appId로 학생 검색 */
    @GetMapping("/students")
    public ResponseEntity<?> searchStudents(@RequestParam("keyword") String keyword) {
        return ResponseEntity.ok(ApiUtils.success(reservationService.searchStudents(keyword)));
    }

    /** 예약 등록 */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody @Valid ReservationReqDTO.RegisterReqDTO reqDTO) {
        reservationService.register(reqDTO.getStudentId(), reqDTO.getReservationDate(), reqDTO.getTimeSlot());
        return ResponseEntity.ok(ApiUtils.success("등록되었습니다."));
    }

    /** 예약 삭제 */
    @DeleteMapping("/delete")
    public ResponseEntity<?> delete(@RequestBody @Valid ReservationReqDTO.DeleteReqDTO reqDTO) {
        reservationService.delete(reqDTO.getReservationId());
        return ResponseEntity.ok(ApiUtils.success("삭제되었습니다."));
    }

}
