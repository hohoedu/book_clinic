package com.hohoedu.book_clinic._core.view;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.hohoedu.book_clinic.clinic.ClinicService;
import com.hohoedu.book_clinic.clinic._dto.ClinicRespDTO;
import com.hohoedu.book_clinic.monitor.MonitorService;
import com.hohoedu.book_clinic.student.StudentRepository;
import com.hohoedu.book_clinic.student.model.Student;

import lombok.RequiredArgsConstructor;

/**
 * 학생 화면 라우팅 — 2026-07-09 클리닉 로직(책 추천/문제풀이/채점) 전체 재설계를 위해
 * ClinicService 연동 부분은 임시로 제거된 상태. 1) 책 추천부터 단계별로 다시 연결한다.
 *
 * 흐름은 로그인 모드로 분기하지 않는다: 로그인 → 책 추천(없으면 새로 추천, 있으면 그 책 그대로) →
 * 로그아웃 → (오프라인 독서) → 재로그인 → 문제풀기 → 결과 확인. 메인 화면은 항상 같은 화면이고,
 * "문제 풀기" 버튼 하나만 있다 — 언제 누를지는 학생 자유.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/student")
public class StudentViewController {

    private final StudentRepository studentRepository;
    private final ClinicService clinicService;
    private final MonitorService monitorService;

    /** 학생 로그인 화면 — 실제 QR 스캔 대신 appId를 직접 입력해서 테스트하는 임시 화면 */
    @GetMapping({ "", "/", "/login" })
    public String getLoginPage() {
        return "/student/student-login";
    }

    /** appId로 로그인 → 성공 시 메인 화면으로 이동 */
    @PostMapping("/login")
    public String login(@RequestParam("appId") String appId, RedirectAttributes redirectAttributes) {
        Student student = studentRepository.findByAppId(appId);
        if (student == null) {
            redirectAttributes.addFlashAttribute("error", "일치하는 학생 정보를 찾을 수 없어요. 다시 확인해주세요.");
            return "redirect:/student/login";
        }
        // 로그인 성공 = 실시간 모니터링 기준 "입실" (오늘 이미 열린 세션이 있으면 그대로 재사용)
        monitorService.enterSession(student.getStudentId());
        return "redirect:/student/main?studentId=" + student.getStudentId();
    }

    /**
     * 레벨 카드는 ClinicService.getMainLevelInfo로 실제 EXP/레벨을 계산해 내려준다.
     * 뱃지 패널은 ClinicService.getEarnedBadges로 획득한 뱃지 전체(최근 획득순)를 내려준다.
     * 추천 도서 카드는 페이지 진입 후 JS가 /clinic/recommend를 호출해 채운다 — 이미 추천받은 책이
     * 있으면 그 책 그대로, 없으면 새로 추천해서 대여까지 확정한다 (ClinicService.recommend가 멱등 처리).
     */
    @GetMapping("/main")
    public String getStudentMainPage(@RequestParam(value = "studentId", required = false) String studentId,
            Model model) {
        if (studentId == null || studentId.isBlank()) {
            return "redirect:/student/login";
        }

        Student student = studentRepository.findById(studentId);
        if (student == null) {
            return "redirect:/student/login";
        }

        model.addAttribute("studentId", studentId);
        model.addAttribute("studentName", student.getStudentName());

        ClinicRespDTO.MainLevelInfoDTO levelInfo = clinicService.getMainLevelInfo(studentId);
        model.addAttribute("levelNo", levelInfo.getLevelNo());
        model.addAttribute("levelName", levelInfo.getLevelName());
        model.addAttribute("feature", levelInfo.getFeature());
        model.addAttribute("characterImg", (String) null);
        model.addAttribute("booksToNextLevel", levelInfo.getBooksToNextLevel());
        model.addAttribute("progressPercent", levelInfo.getProgressPercent());
        model.addAttribute("badges", clinicService.getEarnedBadges(studentId));
        model.addAttribute("monthBooks", clinicService.getMonthBooks(studentId));
        return "/student/student-main";
    }

    /**
     * 문제풀이 화면 — 문제 데이터/도서 표지·제목은 화면 로드 후 JS에서
     * 기존 /question/search, /clinic/recommend API를 호출해 채운다
     */
    /** qlevel: 01=기본(기본값), 02=심화(독서왕/독서친구가 기본 문제풀이 합격 후 추가로 도전) */
    @GetMapping("/question")
    public String getQuestionPage(@RequestParam(value = "studentId", required = false) String studentId,
            @RequestParam(value = "contentId", required = false) Integer contentId,
            @RequestParam(value = "qlevel", required = false, defaultValue = "01") String qlevel,
            Model model) {
        if (studentId == null || studentId.isBlank()) {
            return "redirect:/student/login";
        }

        Student student = studentRepository.findById(studentId);
        if (student == null) {
            return "redirect:/student/login";
        }

        // 문제풀이 화면 진입 = 실시간 모니터링 기준 "문제 푸는 중" 시작 (제출하면 해제됨)
        monitorService.markQuizStarted(studentId);

        model.addAttribute("studentId", studentId);
        model.addAttribute("studentName", student.getStudentName());
        model.addAttribute("contentId", contentId);
        model.addAttribute("qlevel", qlevel);
        return "/student/student-question";
    }

    /**
     * 결과 화면 — 채점 결과(독서왕/독서친구/재도전)는 student-question.js가 채점 직후
     * sessionStorage에 담아 이 화면으로 넘긴다. 새로고침/직접 접근 등으로 결과가 없으면
     * student-result.js가 메인으로 되돌려보낸다.
     */
    @GetMapping("/result")
    public String getResultPage(@RequestParam(value = "studentId", required = false) String studentId,
            @RequestParam(value = "contentId", required = false) Integer contentId,
            @RequestParam(value = "qlevel", required = false, defaultValue = "01") String qlevel,
            Model model) {
        if (studentId == null || studentId.isBlank()) {
            return "redirect:/student/login";
        }

        Student student = studentRepository.findById(studentId);
        if (student == null) {
            return "redirect:/student/login";
        }

        model.addAttribute("studentId", studentId);
        model.addAttribute("studentName", student.getStudentName());
        model.addAttribute("contentId", contentId);
        model.addAttribute("qlevel", qlevel);
        return "/student/student-result";
    }

}
