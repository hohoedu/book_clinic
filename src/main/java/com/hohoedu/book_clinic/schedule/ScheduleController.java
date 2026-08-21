package com.hohoedu.book_clinic.schedule;

import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hohoedu.book_clinic._core.auth.CustomUserDetails;
import com.hohoedu.book_clinic._core.handler.exception.Exception401;
import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic.schedule._dto.ScheduleReqDTO;
import com.hohoedu.book_clinic.schedule.exception.ScheduleExceptionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 운영 스케줄 API (운영관리 > 운영 스케줄) — 요일별 규칙 조회/저장 + 예외 일정 (2026-08-14).
 *
 * 센터는 요청 파라미터로 받지 않고 로그인한 직원의 소속 센터로 고정한다. 다른 센터의 운영
 * 스케줄을 바꿀 수 있는 경로를 아예 만들지 않기 위해서다.
 */
@RestController
@RequestMapping("/admin/operation/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final ScheduleExceptionService scheduleExceptionService;

    /**
     * 요일별 스케줄 조회.
     *
     * @param baseDate 이 날짜에 유효한 버전을 고른다. 생략하면 오늘(KST).
     *                 미래 날짜를 주면 그날 적용될 예정 버전을 미리 볼 수 있다.
     */
    @GetMapping("/week")
    public ResponseEntity<?> week(@RequestParam(value = "baseDate", required = false) String baseDate,
                                  @AuthenticationPrincipal CustomUserDetails userDetails) {
        String centerCode = requireCenterCode(userDetails);
        LocalDate base = baseDate == null || baseDate.isBlank() ? null : LocalDate.parse(baseDate);
        return ResponseEntity.ok(ApiUtils.success(scheduleService.findWeek(centerCode, base)));
    }

    /** 요일별 스케줄 저장 — 적용 시작일(effective_from) 기준의 새 버전을 만든다 */
    @PostMapping("/week")
    public ResponseEntity<?> saveWeek(@RequestBody @Valid ScheduleReqDTO.SaveWeekReqDTO reqDTO,
                                      @AuthenticationPrincipal CustomUserDetails userDetails) {
        String centerCode = requireCenterCode(userDetails);
        String userId = userDetails.getLoginUser().getUserId();
        LocalDate applied = scheduleService.saveWeek(centerCode, userId, reqDTO);
        return ResponseEntity.ok(ApiUtils.success(applied + "부터 적용되도록 저장되었습니다."));
    }

    /**
     * 스케줄 변경 예약 이력 — 적용 시작일 기준 버전 목록(이전/운영중/예정).
     *
     * 버전 상세는 별도 API를 만들지 않는다. 이미 있는 GET /week?baseDate=적용시작일이
     * "그 날짜에 유효한 주간 스케줄"을 그대로 돌려주므로, 화면은 그것을 그대로 쓴다.
     */
    @GetMapping("/versions")
    public ResponseEntity<?> versions(@AuthenticationPrincipal CustomUserDetails userDetails) {
        String centerCode = requireCenterCode(userDetails);
        return ResponseEntity.ok(ApiUtils.success(scheduleService.findVersions(centerCode)));
    }

    /** 예정 버전 삭제 — 지운 기간은 이전 버전 규칙으로 되돌아간다 */
    @DeleteMapping("/version/{effectiveFrom}")
    public ResponseEntity<?> deleteVersion(@PathVariable("effectiveFrom") String effectiveFrom,
                                           @AuthenticationPrincipal CustomUserDetails userDetails) {
        String centerCode = requireCenterCode(userDetails);
        String userId = userDetails.getLoginUser().getUserId();
        scheduleService.deleteVersion(centerCode, userId, LocalDate.parse(effectiveFrom));
        return ResponseEntity.ok(ApiUtils.success("예정된 스케줄 변경이 삭제되었습니다."));
    }

    /** 등록된 예외 일정 목록 — 최근 끝난 것까지 함께 내려준다 */
    @GetMapping("/exception")
    public ResponseEntity<?> exceptions(@AuthenticationPrincipal CustomUserDetails userDetails) {
        String centerCode = requireCenterCode(userDetails);
        return ResponseEntity.ok(ApiUtils.success(scheduleExceptionService.findExceptions(centerCode)));
    }

    /** 예외 일정 등록 — 등록 즉시 해당 기간의 슬롯을 다시 찍는다 */
    @PostMapping("/exception")
    public ResponseEntity<?> createException(@RequestBody @Valid ScheduleReqDTO.SaveExceptionReqDTO reqDTO,
                                             @AuthenticationPrincipal CustomUserDetails userDetails) {
        String centerCode = requireCenterCode(userDetails);
        String userId = userDetails.getLoginUser().getUserId();
        scheduleExceptionService.create(centerCode, userId, reqDTO);
        return ResponseEntity.ok(ApiUtils.success("예외 일정이 등록되었습니다."));
    }

    /**
     * 예외 일정 삭제 — 삭제 후 요일 규칙대로 슬롯이 복구된다.
     * 수정 API가 없어서(결정 2) 화면의 "고치기"도 결국 이 삭제 + 재등록으로 처리된다.
     */
    @DeleteMapping("/exception/{exceptionId}")
    public ResponseEntity<?> deleteException(@PathVariable("exceptionId") Integer exceptionId,
                                             @AuthenticationPrincipal CustomUserDetails userDetails) {
        String centerCode = requireCenterCode(userDetails);
        String userId = userDetails.getLoginUser().getUserId();
        scheduleExceptionService.delete(centerCode, userId, exceptionId);
        return ResponseEntity.ok(ApiUtils.success("예외 일정이 삭제되었습니다."));
    }

    /**
     * SecurityConfig가 이 경로를 authenticated()로 막고 있지만, 인증됐어도 센터가 비어 있는 계정이
     * 있을 수 있다. 센터를 못 정하면 어떤 센터의 데이터를 읽고 쓸지 결정할 수 없으므로 여기서 막는다.
     */
    private String requireCenterCode(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getLoginUser().getCenterCode() == null) {
            throw new Exception401("로그인이 필요합니다.");
        }
        return userDetails.getLoginUser().getCenterCode();
    }

}
