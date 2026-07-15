package com.hohoedu.book_clinic.monitor;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.firebase.auth.FirebaseAuth;
import com.hohoedu.book_clinic._core.handler.exception.Exception500;
import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic.monitor._dto.MonitorReqDTO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 실시간 모니터링(관리자) API — 입실/퇴실 세션 조회, 퇴실 처리, 독서일지 저장 (2026-07-15) */
@RestController
@RequestMapping("/admin/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final MonitorService monitorService;

    /** 화면 최초 진입용 카드 목록 — 이후 갱신은 Firestore 구독으로 받는다 */
    @GetMapping("/live")
    public ResponseEntity<?> live(@RequestParam(value = "date", required = false) String date) {
        LocalDate targetDate = date == null || date.isBlank() ? LocalDate.now() : LocalDate.parse(date);
        return ResponseEntity.ok(ApiUtils.success(monitorService.getLiveView(targetDate)));
    }

    /** 퇴실 처리 */
    @PostMapping("/exit")
    public ResponseEntity<?> exit(@RequestBody @Valid MonitorReqDTO.ExitReqDTO reqDTO) {
        monitorService.exitSession(reqDTO.getStudentId());
        return ResponseEntity.ok(ApiUtils.success(null));
    }

    /** 독서일지 저장(upsert) */
    @PostMapping("/reading-log")
    public ResponseEntity<?> saveReadingLog(@RequestBody @Valid MonitorReqDTO.ReadingLogReqDTO reqDTO,
                                             Authentication authentication) {
        monitorService.saveReadingLog(reqDTO, authentication.getName());
        return ResponseEntity.ok(ApiUtils.success(null));
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
