package com.hohoedu.book_clinic._core.sso;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import com.hohoedu.book_clinic._core.auth.CustomUserDetails;
import com.hohoedu.book_clinic.user.UserRepository;
import com.hohoedu.book_clinic.user.model.User;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 올패스 ↔ 호호책방 직원 계정 SSO 브릿지. 두 앱이 서로 다른 도메인이라 세션을
 * 직접 공유할 수 없어, 원타임 서명 토큰(SsoTokenUtil)으로 상대 서버가 대신
 * 로그인 상태를 만들어준다.
 */
@Controller
@RequestMapping("/sso")
@RequiredArgsConstructor
public class SsoController {

    private final SsoTokenUtil ssoTokenUtil;
    private final UserRepository userRepository;

    @Value("${all-pass.base-url}")
    private String allPassBaseUrl;

    /** 현재 로그인된 직원 그대로 올패스로 이동 */
    @GetMapping("/to-all-pass")
    public String toAllPass(@RequestParam(value = "redirectUrl", required = false) String redirectUrl) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            return "redirect:/login";
        }

        String token;
        try {
            token = ssoTokenUtil.issue(userDetails.getLoginUser().getUserId(), redirectUrl);
        } catch (RuntimeException e) {
            return "redirect:/admin/book-data";
        }
        String url = UriComponentsBuilder.fromUriString(allPassBaseUrl + "/sso/callback")
                .queryParam("token", token)
                .toUriString();
        return "redirect:" + url;
    }

    /** 올패스가 발급한 토큰을 검증하고 호호책방 세션(Spring Security 인증)을 새로 만든다 */
    @GetMapping("/callback")
    public String callback(@RequestParam("token") String token, HttpServletRequest request) {
        SsoTokenUtil.SsoPrincipal principal;
        try {
            principal = ssoTokenUtil.verify(token);
        } catch (RuntimeException e) {
            // IllegalArgumentException(서명/만료/재사용) 외에 IllegalStateException(키 설정 오류)도
            // 500으로 새지 않도록 여기서 함께 막는다 — 어느 쪽이든 사용자에게는 재로그인 유도가 맞다.
            return "redirect:/login";
        }

        User user = userRepository.findByUserId(principal.userId());
        if (user == null) {
            return "redirect:/login";
        }

        CustomUserDetails userDetails = new CustomUserDetails(user);
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute("SPRING_SECURITY_CONTEXT", context);

        String destination = (principal.redirectUrl() != null && !principal.redirectUrl().isBlank())
                ? principal.redirectUrl() : "/admin/book-data";
        return "redirect:" + destination;
    }
}
