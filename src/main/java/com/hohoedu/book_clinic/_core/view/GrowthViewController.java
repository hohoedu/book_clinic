package com.hohoedu.book_clinic._core.view;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class GrowthViewController {

    /** 독서일지  */
    @GetMapping("/admin/growth/diary")
    public String diary() {
        return "growth/diary";
    }

    /** 앱 알림 발송 */
    @GetMapping("/admin/growth/app-send")
    public String appSend() {
        return "growth/app-send";
    }

    /** 학생 성장 분석 — 미구현이라 준비 중 안내만 띄운다 */
    @GetMapping("/admin/growth/student-growth")
    public String studentGrowth(Model model) {
        model.addAttribute("pageTitle", "학생 성장 분석");
        return "common/coming-soon";
    }
}
