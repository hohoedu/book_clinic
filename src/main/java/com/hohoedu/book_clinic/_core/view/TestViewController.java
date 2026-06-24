package com.hohoedu.book_clinic._core.view;

import com.hohoedu.book_clinic._core.utils.HashUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class TestViewController {

    @GetMapping("/test")
    public String test() {
        return "test";
    }

    // data.sql 테스트 계정 설정용 — 초기 세팅 후 제거
    @GetMapping("/test/hash")
    @ResponseBody
    public String hash(@RequestParam String password, @RequestParam String salt) {
        return HashUtils.hashPassword(password, salt);
    }
}
