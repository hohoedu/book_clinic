package com.hohoedu.book_clinic.appsend;

import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hohoedu.book_clinic._core.auth.CustomUserDetails;
import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic._core.utils.KstClock;
import com.hohoedu.book_clinic.appsend._dto.AppSendReqDTO;

import lombok.RequiredArgsConstructor;

/** 독서 결과 발송(관리자) API — 그날 예약 학생별 발송 상태 목록 (2026-09-22) */
@RestController
@RequestMapping("/admin/growth/app-send")
@RequiredArgsConstructor
public class AppSendController {

    private final AppSendService appSendService;

    /**
     * 발송 목록 조회 — 로그인 직원의 센터로 스코핑한다(독서일지·모니터링과 동일 정책).
     * slotSeq(회차)/keyword(학생명)는 선택 필터로, 비어 있으면 그날 전체를 준다.
     */
    @GetMapping("/list")
    public ResponseEntity<?> list(@RequestParam(value = "date", required = false) String date,
                                  @RequestParam(value = "slotSeq", required = false) Integer slotSeq,
                                  @RequestParam(value = "keyword", required = false) String keyword,
                                  @AuthenticationPrincipal CustomUserDetails userDetails) {
        LocalDate targetDate = date == null || date.isBlank() ? KstClock.today() : LocalDate.parse(date);
        String centerCode = userDetails.getLoginUser().getCenterCode();
        return ResponseEntity.ok(ApiUtils.success(
                appSendService.getSendView(targetDate, centerCode, slotSeq, keyword)));
    }

    /**
     * 발송 미리보기 — 목록에서 고른 학생의 그날 정독 결과. 앱의 POST /app/bookstore/report 와
     * 같은 응답이다(같은 {@link com.hohoedu.book_clinic.app.AppReportService} 를 쓴다).
     *
     * 앱 API를 그대로 부르지 못하는 건 그쪽이 세션의 studentId 로만 조회하기 때문이다.
     * 여기서는 studentId 를 받되 센터 확인을 서비스에서 한다.
     *
     * 날짜는 목록과 같은 하루다 — 미리보기에는 앱 화면의 상단 일자 탭이 없다.
     */
    @GetMapping("/report")
    public ResponseEntity<?> report(@RequestParam("studentId") String studentId,
                                    @RequestParam(value = "date", required = false) String date,
                                    @AuthenticationPrincipal CustomUserDetails userDetails) {
        LocalDate targetDate = date == null || date.isBlank() ? KstClock.today() : LocalDate.parse(date);
        String centerCode = userDetails.getLoginUser().getCenterCode();
        return ResponseEntity.ok(ApiUtils.success(
                appSendService.getPreviewReport(studentId, targetDate, centerCode)));
    }

    /**
     * 발송 — 고른 학생들의 일지 is_send 를 1 로 올린다. 이때부터 앱 정독 결과에 보인다.
     *
     * 플래그를 올리는 동시에 그 학생 앱으로 결과 도착 푸시가 나간다(AppSendService.send).
     *
     * 응답은 실제로 바뀐 건수다. 화면이 보낸 것 중 이미 발송됐던 건(다른 직원이 방금 눌렀다든지)
     * 은 빠지므로, 요청 수와 응답 수가 다를 수 있다.
     */
    @PostMapping("/send")
    public ResponseEntity<?> send(@RequestBody AppSendReqDTO.SendDTO reqDTO,
                                  @AuthenticationPrincipal CustomUserDetails userDetails) {
        String centerCode = userDetails.getLoginUser().getCenterCode();
        String sentBy = userDetails.getLoginUser().getUserId();
        return ResponseEntity.ok(ApiUtils.success(
                appSendService.send(reqDTO.getDiaryKeys(), centerCode, sentBy)));
    }
}
