package com.hohoedu.book_clinic._core.view;

import com.hohoedu.book_clinic._core.utils.HashUtils;
import com.hohoedu.book_clinic.student.StudentService;
import com.hohoedu.book_clinic.student._dto.StudentRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class TestViewController {

    private final StudentService studentService;

    @GetMapping("/test")
    public String test() {
        return "test";
    }

    // data.sql 테스트 계정 설정용 — 초기 세팅 후 제거
    @GetMapping("/test/hash")
    @ResponseBody
    public String hash(@RequestParam(value = "password") String password, @RequestParam(value = "salt") String salt) {
        return HashUtils.hashPassword(password, salt);
    }

    @PostMapping("/test/studentInfo")
    @ResponseBody
    public List<StudentRespDTO.StudentInfoRowDTO> studentInfo(@RequestBody Map<String, String> request) {
        String centerCode = request.get("centerCode");
        System.out.println("centerCode = " + centerCode);
        return studentService.getStudentInfoList(centerCode);
    }
}
