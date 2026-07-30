package com.hohoedu.book_clinic._core.view;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.hohoedu.book_clinic.clinic.ClinicService;
import com.hohoedu.book_clinic.clinic._dto.ClinicRespDTO;
import com.hohoedu.book_clinic.student.StudentRepository;
import com.hohoedu.book_clinic.student.model.Student;

import lombok.RequiredArgsConstructor;

/**
 * 출석체크 화면 — 문제풀이 PWA와 같은 앱(manifest.json) 안의 한 화면일 뿐이다(2026-07-30, 재설계).
 * 처음엔 별도 PWA(별도 manifest+설치 페이지)로 만들었다가, 같은 origin에서 설치 가능한 PWA를
 * 2개로 나누는 게 브라우저별로 안정적이지 않아(설치 프롬프트가 잘 안 뜸) LauncherViewController의
 * 분기(/launch)로 되돌렸다 — 앱은 하나, 그 안에서 "문제풀이"/"출석체크"만 고른다.
 *
 * 입실: QR 스캔 → /attendance/enter?appId=X로 이동 — 입실 처리 + 오늘 추천받은 책만 보여주는
 *      확인 화면으로 간다("닫기"를 누르면 이 출석체크 홈으로 복귀, 문제풀이로는 못 넘어간다 —
 *      출석체크 키오스크는 여러 학생이 돌려쓰는 공용 기기라 여기서 실제 학습까지 이어지면 안 된다).
 *      실제 학습(문제 풀기)은 학생 개인 폰의 문제풀이 앱(/student/login)에서 별도로 한다.
 * 퇴실: QR 스캔 → /student/exit 호출(로그인 컨텍스트 없이 studentId 생략) → 완료 메시지 후 이 화면으로 복귀
 */
@Controller
@RequestMapping("/attendance")
@RequiredArgsConstructor
public class AttendanceViewController {

    private final StudentRepository studentRepository;
    private final ClinicService clinicService;

    @GetMapping({ "", "/" })
    public String getHomePage() {
        return "/attendance/attendance-home";
    }

    /**
     * QR 스캔으로 찾은 학생의 입실을 처리하고, 오늘 추천받은 책만 확인시켜주는 화면.
     * ClinicService.getHomeState가 내부적으로 enterSession까지 처리한다(기존 로그인 흐름과 동일).
     */
    @GetMapping("/enter")
    public String enter(@RequestParam("appId") String appId, Model model) {
        Student student = studentRepository.findByAppId(appId);
        if (student == null) {
            model.addAttribute("error", "일치하는 학생 정보를 찾을 수 없어요. QR을 다시 스캔해주세요.");
            return "/attendance/book-confirm";
        }

        ClinicRespDTO.BookStatusRespDTO homeState = clinicService.getHomeState(student.getStudentId());
        model.addAttribute("studentName", student.getStudentName());
        model.addAttribute("book", homeState.getBook());
        return "/attendance/book-confirm";
    }
}
