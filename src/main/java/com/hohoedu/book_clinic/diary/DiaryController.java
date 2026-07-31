package com.hohoedu.book_clinic.diary;

import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hohoedu.book_clinic._core.auth.CustomUserDetails;
import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic._core.utils.KstClock;
import com.hohoedu.book_clinic.diary._dto.DiaryReqDTO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 독서일지(관리자) API — 예약 기준 목록에 일지 데이터를 덮어 내려준다 (2026-07-30) */
@RestController
@RequestMapping("/admin/growth/diary")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;

    /**
     * 일지 목록 조회 — 로그인 직원의 센터로 스코핑한다(모니터링과 동일 정책).
     * timeSlot('1'~'4')/keyword(학생명)는 선택 필터로, 비어 있으면 그날 전체를 준다.
     */
    @GetMapping("/list")
    public ResponseEntity<?> list(@RequestParam(value = "date", required = false) String date,
                                  @RequestParam(value = "timeSlot", required = false) String timeSlot,
                                  @RequestParam(value = "keyword", required = false) String keyword,
                                  @AuthenticationPrincipal CustomUserDetails userDetails) {
        LocalDate targetDate = date == null || date.isBlank() ? KstClock.today() : LocalDate.parse(date);
        String centerCode = userDetails.getLoginUser().getCenterCode();
        return ResponseEntity.ok(ApiUtils.success(
                diaryService.getDiaryView(targetDate, centerCode, timeSlot, keyword)));
    }

    /** 독서태도·기타 전달사항 일괄 저장 — 상단 "저장" 버튼(바뀐 행만 담겨 온다) */
    @PostMapping("/save")
    public ResponseEntity<?> save(@RequestBody @Valid DiaryReqDTO.BulkSaveReqDTO reqDTO,
                                  @AuthenticationPrincipal CustomUserDetails userDetails,
                                  Authentication authentication) {
        String centerCode = userDetails.getLoginUser().getCenterCode();
        int saved = diaryService.saveDiaries(reqDTO, centerCode, authentication.getName());
        return ResponseEntity.ok(ApiUtils.success(saved + "건 저장되었습니다."));
    }

    /**
     * 등/하원 시각 변경 — 행의 "변경" 버튼. 일지 헤더의 in_time/out_time만 갱신하고,
     * 저장된 값을 그대로 돌려줘서 화면이 입력칸 기준값을 다시 맞출 수 있게 한다.
     */
    @PostMapping("/time")
    public ResponseEntity<?> saveTime(@RequestBody @Valid DiaryReqDTO.TimeReqDTO reqDTO,
                                      @AuthenticationPrincipal CustomUserDetails userDetails) {
        String centerCode = userDetails.getLoginUser().getCenterCode();
        return ResponseEntity.ok(ApiUtils.success(diaryService.saveDiaryTime(reqDTO, centerCode)));
    }

}
