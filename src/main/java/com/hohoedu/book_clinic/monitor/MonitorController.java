package com.hohoedu.book_clinic.monitor;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.firebase.auth.FirebaseAuth;
import com.hohoedu.book_clinic._core.auth.CenterAccessGuard;
import com.hohoedu.book_clinic._core.auth.CustomUserDetails;
import com.hohoedu.book_clinic._core.handler.exception.Exception500;
import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic._core.utils.KstClock;
import com.hohoedu.book_clinic.clinic.ClinicService;
import com.hohoedu.book_clinic.monitor._dto.MonitorReqDTO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 실시간 모니터링(관리자) API — 입실/퇴실 세션 조회, 퇴실 처리, 독서일지 저장 (2026-07-15) */
@RestController
@RequestMapping("/admin/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final MonitorService monitorService;
    private final ClinicService clinicService;
    private final CenterAccessGuard centerAccessGuard;

    /** 화면 최초 진입용 카드 목록 — 이후 갱신은 Firestore 구독으로 받는다 */
    @GetMapping("/live")
    public ResponseEntity<?> live(@RequestParam(value = "date", required = false) String date,
                                  @AuthenticationPrincipal CustomUserDetails userDetails) {
        LocalDate targetDate = date == null || date.isBlank() ? KstClock.today() : LocalDate.parse(date);
        String centerCode = centerAccessGuard.requireCenterCode(userDetails);
        return ResponseEntity.ok(ApiUtils.success(monitorService.getLiveView(targetDate, centerCode)));
    }

    /**
     * 퇴실 처리 — 대상 학생을 요청 본문으로 지정하므로 로그인한 직원의 센터 소속인지 대조한다.
     * 검증이 없으면 다른 센터 관리자가 남의 센터 학생을 임의로 강제 퇴실시킬 수 있었다(2026-08-20).
     */
    @PostMapping("/exit")
    public ResponseEntity<?> exit(@RequestBody @Valid MonitorReqDTO.ExitReqDTO reqDTO,
                                  @AuthenticationPrincipal CustomUserDetails userDetails) {
        centerAccessGuard.requireStudentInMyCenter(userDetails, reqDTO.getStudentId());
        monitorService.exitSession(reqDTO.getStudentId());
        return ResponseEntity.ok(ApiUtils.success(null));
    }

    /**
     * 독서일지 저장(upsert) — exit과 같은 이유로 대상 학생의 센터를 대조한다.
     * sessionId가 정말 그 학생의 세션인지는 MonitorService.saveDiary가 한 번 더 확인한다
     * (내 센터 학생 이름표를 달고 남의 센터 세션에 일지를 쓰는 것을 막는다).
     */
    @PostMapping("/diary")
    public ResponseEntity<?> saveDiary(@RequestBody @Valid MonitorReqDTO.DiaryReqDTO reqDTO,
                                       @AuthenticationPrincipal CustomUserDetails userDetails) {
        centerAccessGuard.requireStudentInMyCenter(userDetails, reqDTO.getStudentId());
        monitorService.saveDiary(reqDTO, userDetails.getUsername());
        return ResponseEntity.ok(ApiUtils.success(null));
    }

    /**
     * 문제풀이 기록 삭제(초기화) — 학생 요청으로 직원이 카드에서 실행한다. 그 책 한 권의 풀이 이력과
     * 뱃지/카드를 회수하고 추천을 "문제 풀기 전"으로 되돌린다. 삭제 이력은 서버가 남긴다.
     */
    @PostMapping("/quiz/reset")
    public ResponseEntity<?> resetQuiz(@RequestBody @Valid MonitorReqDTO.QuizResetReqDTO reqDTO,
                                       @AuthenticationPrincipal CustomUserDetails userDetails) {
        centerAccessGuard.requireStudentInMyCenter(userDetails, reqDTO.getStudentId());
        return ResponseEntity.ok(ApiUtils.success(monitorService.resetQuiz(reqDTO, userDetails.getUsername())));
    }

    /**
     * 추천 도서 교체 — 추천된 책이 서가에 실제로 없거나 못 읽을 정도로 훼손됐을 때 직원이 카드에서
     * 실행한다. 지금 추천을 취소하고(그 한 권은 재고에서 빠진다) 곧바로 다음 책을 추천한다.
     * exit/quiz-reset과 같은 이유로 대상 학생이 내 센터 소속인지 대조한다.
     */
    @PostMapping("/recommend/cancel")
    public ResponseEntity<?> cancelRecommend(@RequestBody @Valid MonitorReqDTO.CancelRecommendReqDTO reqDTO,
                                             @AuthenticationPrincipal CustomUserDetails userDetails) {
        centerAccessGuard.requireStudentInMyCenter(userDetails, reqDTO.getStudentId());
        return ResponseEntity.ok(ApiUtils.success(
                clinicService.replaceRecommendedBook(reqDTO, userDetails.getUsername())));
    }

    /**
     * 관리자 브라우저가 Firestore를 직접 구독하기 위한 커스텀 토큰 발급.
     * Firestore 보안 규칙은 "인증된 사용자만 read, write는 서버(Admin SDK)만"으로 잠그는 것을
     * 전제로 한다 — 이 엔드포인트는 Spring Security로 이미 로그인된 admin만 호출 가능(/admin/**).
     */
    @PostMapping("/firebase-token")
    public ResponseEntity<?> firebaseToken(Authentication authentication) {
        try {
            String token = FirebaseAuth.getInstance().createCustomToken(authentication.getName());
            return ResponseEntity.ok(ApiUtils.success(Map.of("token", token)));
        } catch (Exception e) {
            throw new Exception500("Firebase 커스텀 토큰 발급에 실패했습니다.");
        }
    }

}
