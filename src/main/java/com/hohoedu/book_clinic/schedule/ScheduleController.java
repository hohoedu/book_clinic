package com.hohoedu.book_clinic.schedule;

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
import com.hohoedu.book_clinic.schedule._dto.ScheduleReqDTO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 운영 스케줄 API (운영관리 > 운영 스케줄) — 요일별 규칙 조회/저장 (2026-08-14).
 *
 * 센터는 요청 파라미터로 받지 않고 로그인한 직원의 소속 센터로 고정한다. 다른 센터의 운영
 * 스케줄을 바꿀 수 있는 경로를 아예 만들지 않기 위해서다.
 */
@RestController
@RequestMapping("/admin/operation/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

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
     * 화면(/admin/operation/schedule)이 개발 편의상 permitAll로 열려 있어 로그인 없이도 들어올 수
     * 있다. 센터를 못 정하면 어떤 센터의 데이터를 읽고 쓸지 결정할 수 없으므로 여기서 막는다.
     */
    private String requireCenterCode(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getLoginUser().getCenterCode() == null) {
            throw new Exception401("로그인이 필요합니다.");
        }
        return userDetails.getLoginUser().getCenterCode();
    }

}
