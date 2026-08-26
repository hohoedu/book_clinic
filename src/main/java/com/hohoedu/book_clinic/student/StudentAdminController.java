package com.hohoedu.book_clinic.student;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hohoedu.book_clinic._core.auth.CenterAccessGuard;
import com.hohoedu.book_clinic._core.auth.CustomUserDetails;
import com.hohoedu.book_clinic._core.utils.ApiUtils;

import lombok.RequiredArgsConstructor;

/**
 * "학생 정보(회원 현황)" 화면 조회 API (2026-08-26). 목록/상세/독서이력/예약현황 전부 읽기 전용이고,
 * 담당선생님/회비 같은 DB에 없는 필드는 여기서 내려주지 않는다(화면이 계속 목업으로 채운다).
 */
@RestController
@RequestMapping("/admin/students")
@RequiredArgsConstructor
public class StudentAdminController {

    private final StudentService studentService;
    private final CenterAccessGuard centerAccessGuard;

    /** 학생 목록 — 학년/등록상태/검색어(이름 또는 연락처) 필터, 전부 선택값 */
    @GetMapping("/list")
    public ResponseEntity<?> list(@RequestParam(value = "grade", required = false) String grade,
                                  @RequestParam(value = "status", required = false) String status,
                                  @RequestParam(value = "keyword", required = false) String keyword,
                                  @AuthenticationPrincipal CustomUserDetails userDetails) {
        String centerCode = centerAccessGuard.requireCenterCode(userDetails);
        return ResponseEntity.ok(ApiUtils.success(
                studentService.getStudentInfoList(centerCode, grade, status, keyword)));
    }

    /** 필터바 학년 드롭다운 옵션 */
    @GetMapping("/grade-options")
    public ResponseEntity<?> gradeOptions() {
        return ResponseEntity.ok(ApiUtils.success(studentService.getGradeOptions()));
    }

    /** 상세모달 — 기본 정보/레벨/누적 통계 */
    @GetMapping("/{studentId}")
    public ResponseEntity<?> detail(@PathVariable("studentId") String studentId,
                                    @AuthenticationPrincipal CustomUserDetails userDetails) {
        centerAccessGuard.requireStudentInMyCenter(userDetails, studentId);
        return ResponseEntity.ok(ApiUtils.success(studentService.getStudentDetail(studentId)));
    }

    /** 상세모달 독서이력 탭 */
    @GetMapping("/{studentId}/reading-history")
    public ResponseEntity<?> readingHistory(@PathVariable("studentId") String studentId,
                                            @AuthenticationPrincipal CustomUserDetails userDetails) {
        centerAccessGuard.requireStudentInMyCenter(userDetails, studentId);
        return ResponseEntity.ok(ApiUtils.success(studentService.getReadingHistory(studentId)));
    }

    /** 상세모달 예약현황 탭 */
    @GetMapping("/{studentId}/reservations")
    public ResponseEntity<?> reservations(@PathVariable("studentId") String studentId,
                                          @AuthenticationPrincipal CustomUserDetails userDetails) {
        centerAccessGuard.requireStudentInMyCenter(userDetails, studentId);
        return ResponseEntity.ok(ApiUtils.success(studentService.getReservationHistory(studentId)));
    }
}
