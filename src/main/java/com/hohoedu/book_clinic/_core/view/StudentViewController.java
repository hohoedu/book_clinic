package com.hohoedu.book_clinic._core.view;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.hohoedu.book_clinic.clinic.ClinicService;
import com.hohoedu.book_clinic.student.StudentRepository;
import com.hohoedu.book_clinic.student.model.Student;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/student")
public class StudentViewController {

    // TODO: QR 로그인 도입 전까지 데모 학생으로 고정 (studentId 파라미터 없이 /main 직접 접근 시)
    private static final String DEMO_STUDENT_ID = "PUS001251202FDA6E";

    private final ClinicService clinicService;
    private final StudentRepository studentRepository;

    /** 학생 로그인 화면 — 실제 QR 스캔 대신 appId를 직접 입력해서 테스트하는 임시 화면 */
    @GetMapping("/login")
    public String getLoginPage() {
        return "/student-login";
    }

    /** appId로 로그인 → 성공 시 책 추천이 포함된 메인 화면으로 이동 */
    @PostMapping("/login")
    public String login(@RequestParam("appId") String appId, RedirectAttributes redirectAttributes) {
        Student student = studentRepository.findByAppId(appId);
        if (student == null) {
            redirectAttributes.addFlashAttribute("error", "일치하는 학생 정보를 찾을 수 없어요. 다시 확인해주세요.");
            return "redirect:/student/login";
        }
        return "redirect:/student/main?studentId=" + student.getStudentId();
    }

    /** 임시 디버그용 — 추천 후보 순서/제외 목록/최근 완독 분류·장르/최종 선택 결과 확인 (진단 후 제거 예정) */
    @GetMapping("/debug/recommend")
    @ResponseBody
    public Object debugRecommend(@RequestParam(value = "studentId", required = false) String studentId) {
        String id = (studentId == null || studentId.isBlank()) ? DEMO_STUDENT_ID : studentId;
        return clinicService.debugRecommend(id);
    }

    /** 로그인 없이 studentId 없이 직접 접근하면 로그인 화면으로 되돌린다 (PWA/키오스크의 로그인 → 메인 흐름 강제) */
    @GetMapping("/main")
    public String getStudentMainPage(@RequestParam(value = "studentId", required = false) String studentId,
                                     Model model) {
        if (studentId == null || studentId.isBlank()) {
            return "redirect:/student/login";
        }

        model.addAttribute("student", clinicService.findStudentInfo(studentId));
        model.addAttribute("badges", clinicService.findBadges(studentId));
        model.addAttribute("monthBooks", clinicService.findMonthBooks(studentId));
        model.addAttribute("recommend", clinicService.recommendBook(studentId));
        return "/student-main";
    }

}
