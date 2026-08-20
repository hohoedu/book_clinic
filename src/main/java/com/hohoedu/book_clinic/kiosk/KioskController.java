package com.hohoedu.book_clinic.kiosk;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hohoedu.book_clinic._core.auth.CustomUserDetails;
import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic._core.utils.CenterPolicy;
import com.hohoedu.book_clinic.kiosk._dto.KioskReqDTO;
import com.hohoedu.book_clinic.kiosk._dto.KioskRespDTO;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 센터 기기 라이선스 API (2026-08-20).
 *
 * 두 종류가 한 파일에 있다 — 발급/목록/폐기는 본사 직원이 관리자 화면에서 쓰고(assertHq),
 * 등록(/kiosk/register)은 아직 키가 없는 센터 기기가 부른다. 등록만 인증 없이 열려 있는데,
 * 유효한 키 원문을 알아야만 통과하므로 그 자체가 자격 증명이다.
 */
@RestController
@RequiredArgsConstructor
public class KioskController {

    private final KioskService kioskService;

    // ── 본사 (운영 > 기기 라이선스) ────────────────────────────

    @GetMapping("/admin/operation/kiosk/centers")
    public ResponseEntity<?> centerOptions(@AuthenticationPrincipal CustomUserDetails userDetails) {
        CenterPolicy.assertHq(userDetails);
        return ResponseEntity.ok(ApiUtils.success(kioskService.findCenterOptions()));
    }

    @GetMapping("/admin/operation/kiosk")
    public ResponseEntity<?> licenses(@RequestParam(value = "centerCode", required = false) String centerCode,
                                      @AuthenticationPrincipal CustomUserDetails userDetails) {
        CenterPolicy.assertHq(userDetails);
        return ResponseEntity.ok(ApiUtils.success(kioskService.findLicenses(centerCode)));
    }

    /** 발급 — 응답에 키 원문이 실리는 유일한 지점. 화면이 이걸 보여주고 본사가 센터에 전달한다 */
    @PostMapping("/admin/operation/kiosk")
    public ResponseEntity<?> issue(@RequestBody @Valid KioskReqDTO.IssueReqDTO reqDTO,
                                   @AuthenticationPrincipal CustomUserDetails userDetails) {
        CenterPolicy.assertHq(userDetails);
        return ResponseEntity.ok(ApiUtils.success(
                kioskService.issue(reqDTO.getCenterCode(), reqDTO.getLabel(),
                        reqDTO.getDeviceLimit(), userDetails.getUsername())));
    }

    /** 이 키로 등록된 기기 목록 — 몇 대가 어디서 쓰는지 확인용 */
    @GetMapping("/admin/operation/kiosk/{licenseId}/devices")
    public ResponseEntity<?> devices(@PathVariable("licenseId") Integer licenseId,
                                     @AuthenticationPrincipal CustomUserDetails userDetails) {
        CenterPolicy.assertHq(userDetails);
        return ResponseEntity.ok(ApiUtils.success(kioskService.findDevices(licenseId)));
    }

    /** 키 폐기 — 이 키로 등록된 기기가 전부 함께 막힌다 */
    @DeleteMapping("/admin/operation/kiosk/{licenseId}")
    public ResponseEntity<?> revoke(@PathVariable("licenseId") Integer licenseId,
                                    @AuthenticationPrincipal CustomUserDetails userDetails) {
        CenterPolicy.assertHq(userDetails);
        kioskService.revoke(licenseId, userDetails.getUsername());
        return ResponseEntity.ok(ApiUtils.success("키가 폐기되었습니다. 이 키로 등록된 기기가 모두 막힙니다."));
    }

    /** 기기 1대만 폐기 — 나머지 기기는 계속 쓸 수 있다 */
    @DeleteMapping("/admin/operation/kiosk/device/{deviceId}")
    public ResponseEntity<?> revokeDevice(@PathVariable("deviceId") Integer deviceId,
                                          @AuthenticationPrincipal CustomUserDetails userDetails) {
        CenterPolicy.assertHq(userDetails);
        kioskService.revokeDevice(deviceId, userDetails.getUsername());
        return ResponseEntity.ok(ApiUtils.success("기기가 폐기되었습니다."));
    }

    // ── 센터 기기 ───────────────────────────────────────────

    /**
     * 기기 등록 — 본사에서 받은 키를 설치 시 한 번 입력한다.
     *
     * 키를 응답 본문이 아니라 HttpOnly 쿠키로 심는다. 이유가 둘이다.
     *   · /student/login은 폼 전송이라 헤더를 실을 수 없다. 쿠키면 폼·네비게이션·fetch가 전부
     *     자동으로 들고 가므로 기존 호출부를 하나도 고치지 않아도 된다.
     *   · JS가 읽을 수 없어 XSS로 키를 빼갈 수 없다(기기를 직접 만지는 경우는 별개 문제다).
     * 유효기간은 1년 — 기기 등록은 현장에서 사람이 하는 작업이라 자주 다시 하게 만들면 안 된다.
     */
    @PostMapping("/kiosk/register")
    public ResponseEntity<?> register(@RequestBody @Valid KioskReqDTO.RegisterReqDTO reqDTO,
                                      HttpServletRequest request,
                                      HttpServletResponse response) {
        KioskRespDTO.RegisteredDTO registered =
                kioskService.register(reqDTO.getLicenseKey(), request.getHeader("User-Agent"));

        // 쿠키에는 라이선스 키가 아니라 이 기기 몫의 토큰이 들어간다 — 키 원문이 기기에 남으면
        // 그걸 꺼내 다른 기기를 무제한으로 붙일 수 있다.
        response.addHeader("Set-Cookie", deviceCookie(registered.getDeviceToken(), Duration.ofDays(365)));
        return ResponseEntity.ok(ApiUtils.success(registered.getCenterCode()));
    }

    /** 등록 해제 — 기기를 반납하거나 다른 센터로 옮길 때 센터 직원이 기기에서 직접 실행한다 */
    @DeleteMapping("/kiosk/register")
    public ResponseEntity<?> unregister(HttpServletRequest request, HttpServletResponse response) {
        KioskRespDTO.ResolvedDTO resolved = kioskService.resolveFromRequest(request);
        kioskService.unregister(resolved == null ? null : readDeviceToken(request), "kiosk");

        response.addHeader("Set-Cookie", deviceCookie("", Duration.ZERO));
        return ResponseEntity.ok(ApiUtils.success("기기 등록이 해제되었습니다."));
    }

    private String deviceCookie(String value, Duration maxAge) {
        return ResponseCookie.from(KioskService.COOKIE_NAME, value)
                .httpOnly(true)
                .path("/")
                .maxAge(maxAge)
                .sameSite("Lax")
                .build()
                .toString();
    }

    private String readDeviceToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (KioskService.COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
