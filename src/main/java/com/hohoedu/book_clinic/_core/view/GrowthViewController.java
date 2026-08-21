package com.hohoedu.book_clinic._core.view;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class GrowthViewController {

    /** 독서일지 — 정적 스캐폴딩 단계, 조회/저장 API 연동은 다음 작업에서 이어감 (2026-07-29) */
    @GetMapping("/admin/growth/diary")
    public String diary() {
        return "growth/diary";
    }
}
