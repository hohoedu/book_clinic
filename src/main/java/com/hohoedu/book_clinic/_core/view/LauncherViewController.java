package com.hohoedu.book_clinic._core.view;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import lombok.RequiredArgsConstructor;

/**
 * 앱 하나(manifest.json)로 설치된 PWA의 진짜 진입점(start_url)이다(2026-07-30).
 * 처음 켰을 때만 "문제풀이"/"출석체크"를 고르게 하고, 그 선택은 브라우저 localStorage에 저장해
 * 이후로는 이 화면 없이 바로 골랐던 화면으로 넘어간다(launcher.js가 처리) — 실제 라우팅 분기는
 * 서버가 아니라 클라이언트에서 한다(로그인 여부와 무관하게 그냥 "이 기기 용도"만 기억하면 되므로).
 *
 * dev 프로파일에서는 테스트 중 두 화면을 계속 오가며 확인해야 해서, 저장된 값이 있어도 매번
 * 선택 화면을 다시 보여준다(2026-07-30) — prod에서는 원래대로 한 번만 물어본다.
 */
@Controller
@RequiredArgsConstructor
public class LauncherViewController {

    private final Environment environment;

    @GetMapping("/launch")
    public String getLauncherPage(Model model) {
        boolean isDev = environment.matchesProfiles("dev");
        model.addAttribute("forceChoice", isDev);
        return "/launcher";
    }
}
