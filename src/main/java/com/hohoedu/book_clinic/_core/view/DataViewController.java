package com.hohoedu.book_clinic._core.view;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.hohoedu.book_clinic._core.auth.CustomUserDetails;
import com.hohoedu.book_clinic._core.utils.CenterPolicy;
import com.hohoedu.book_clinic.common.code.CodeService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class DataViewController {

    private final CodeService codeService;

    /** 루트(로그인 직후 포함) — 센터별 첫 화면으로 보낸다 */
    @GetMapping("/")
    public String home(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return "redirect:" + CenterPolicy.homeUrl(userDetails);
    }

    /** 관리자 로그인 — 이미 로그인된 상태면 센터별 첫 화면으로 보낸다. 실패 사유는 Spring Security가 세션에 남긴 예외 메시지 */
    @GetMapping("/login")
    public String login(@AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String error, HttpSession session, Model model) {
        if (userDetails != null) {
            return "redirect:" + CenterPolicy.homeUrl(userDetails);
        }
        if (error != null) {
            Object ex = session.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
            model.addAttribute("errorMessage", ex instanceof AuthenticationException ae && ae.getMessage() != null
                    ? ae.getMessage() : "로그인에 실패했습니다.");
        }
        return "login";
    }

    /** 도서 데이터 — 도서 정보를 조회/조정하는 화면 */
    @GetMapping("/admin/book-data")
    public String bookData(Model model) {
        model.addAttribute("contentTypeCodes", codeService.findBookstoreCodes("C"));
        model.addAttribute("genreCodes", codeService.findBookstoreCodes("G"));
        return "data/book-data";
    }

    /** 도서 우선순위 설정 — 도서별 우선순위를 조회/조정하는 화면 */
    @GetMapping("/admin/book-priority")
    public String bookPriority() {
        return "data/book-priority";
    }

    /** 레벨/뱃지 관리 — 미구현이라 준비 중 안내만 띄운다 */
    @GetMapping("/admin/level-badge")
    public String levelBadge(Model model) {
        model.addAttribute("pageTitle", "레벨/뱃지 관리");
        return "common/coming-soon";
    }
}
