package com.hohoedu.book_clinic._core.view;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.hohoedu.book_clinic.common.code.CodeService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class DataViewController {

    private final CodeService codeService;

    /** 도서 데이터 — 도서 정보를 조회/조정하는 화면 */
    @GetMapping({ "/", "/admin/book-data" })
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
}
