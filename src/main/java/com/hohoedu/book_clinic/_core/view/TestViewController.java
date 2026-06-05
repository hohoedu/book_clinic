package com.hohoedu.book_clinic._core.view;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class TestViewController {

    @GetMapping("/test")
    public String getMethodName(@RequestParam String param) {
        return new String();
    }

}
