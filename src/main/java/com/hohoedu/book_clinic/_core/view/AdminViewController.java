package com.hohoedu.book_clinic._core.view;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.hohoedu.book_clinic.common.code.CodeService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class AdminViewController {

    private final CodeService codeService;

    @GetMapping("/admin/book-data")
    public String bookData(Model model) {
        // 분류(대분류)·장르 코드 - 화면 chip/셀렉트/필터 렌더링용
        model.addAttribute("contentTypeCodes", codeService.findBookstoreCodes("C"));
        model.addAttribute("genreCodes", codeService.findBookstoreCodes("G"));
        return "book-data";
    }

    @GetMapping("/admin/book-priority")
    public String bookPriority() {
        return "book-priority";
    }
}
