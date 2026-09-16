package com.hohoedu.book_clinic.student;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hohoedu.book_clinic._core.auth.CenterAccessGuard;
import com.hohoedu.book_clinic._core.auth.CustomUserDetails;
import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic.student._dto.StudentReqDTO;

import lombok.RequiredArgsConstructor;

/**
 * "학생 정보(회원 현황)" 화면 API (2026-08-26). 목록/상세/독서이력/예약현황은 읽기 전용이고,
 * 수강 정보(bookstore-assign) 탭만 조회 + 저장을 함께 제공한다(2026-09-15).
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

    /** 상세모달 수강 정보 탭 — 저장 전이면 화면용 기본값이 saved=false 로 내려온다 */
    @GetMapping("/{studentId}/bookstore-assign")
    public ResponseEntity<?> bookstoreAssign(@PathVariable("studentId") String studentId,
                                             @AuthenticationPrincipal CustomUserDetails userDetails) {
        centerAccessGuard.requireStudentInMyCenter(userDetails, studentId);
        return ResponseEntity.ok(ApiUtils.success(studentService.getBookstoreAssign(studentId)));
    }

    /** 상세모달 수강 정보 탭 저장 (upsert) */
    @PutMapping("/{studentId}/bookstore-assign")
    public ResponseEntity<?> saveBookstoreAssign(@PathVariable("studentId") String studentId,
                                                 @RequestBody StudentReqDTO.BookstoreAssignSaveDTO reqDTO,
                                                 @AuthenticationPrincipal CustomUserDetails userDetails) {
        centerAccessGuard.requireStudentInMyCenter(userDetails, studentId);
        studentService.saveBookstoreAssign(studentId, reqDTO);
        return ResponseEntity.ok(ApiUtils.success(studentService.getBookstoreAssign(studentId)));
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
