package com.hohoedu.book_clinic._core.view;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.hohoedu.book_clinic.clinic.ClinicService;
import com.hohoedu.book_clinic.clinic._dto.ClinicRespDTO;
import com.hohoedu.book_clinic.kiosk.KioskService;
import com.hohoedu.book_clinic.student.StudentRepository;
import com.hohoedu.book_clinic.student.model.Student;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 출석체크 화면 — 문제풀이 PWA와 같은 앱(manifest.json) 안의 한 화면일 뿐이다(2026-07-30, 재설계).
 * 처음엔 별도 PWA(별도 manifest+설치 페이지)로 만들었다가, 같은 origin에서 설치 가능한 PWA를
 * 2개로 나누는 게 브라우저별로 안정적이지 않아(설치 프롬프트가 잘 안 뜸) LauncherViewController의
 * 분기(/launch)로 되돌렸다 — 앱은 하나, 그 안에서 "문제풀이"/"출석체크"만 고른다.
 *
 * 입실: QR 스캔 → /attendance/enter로 POST — 입실 처리 + 오늘 추천받은 책만 보여주는
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
    private final KioskService kioskService;

    /**
     * 출석체크 홈. 기기 키가 없으면 입실/퇴실 대신 등록 안내를 보여준다 — 쿠키가 HttpOnly라
     * 화면 JS가 직접 못 읽으므로 서버가 판단해서 내려준다.
     */
    @GetMapping({ "", "/" })
    public String getHomePage(Model model, HttpServletRequest request) {
        model.addAttribute("registered", kioskService.resolveFromRequest(request) != null);
        return "/attendance/attendance-home";
    }

    /**
     * QR 스캔으로 찾은 학생의 입실을 처리하고, 오늘 추천받은 책만 확인시켜주는 화면.
     * ClinicService.getHomeState가 내부적으로 enterSession까지 처리한다(기존 로그인 흐름과 동일).
     *
     * [왜 POST인가] 이 요청은 화면을 보여주기만 하는 게 아니라 입실 세션을 만들고 이용권을
     * 1회 소진시킨다(MonitorService.enterSession → PassService.consume). GET이었을 때는
     * 주소창에 `/attendance/enter?appId=7001`을 치는 것만으로 남의 이용권이 깎였고, app_id가
     * 4자리라 대입도 쉬웠다(2026-08-20 발견). POST로 바꾸면 CSRF 필터가 걸려(/attendance/**는
     * CSRF 예외 목록에 없다) 우리 화면에서 온 요청만 통과한다.
     *
     * 새로고침으로 다시 POST돼도 안전하다 — enterSession은 열린 세션이 있으면 재사용하고
     * markAttended/consume 모두 같은 세션에 대해 멱등이다.
     */
    @PostMapping("/enter")
    public String enter(@RequestParam("appId") String appId, Model model, HttpServletRequest request) {
        Student student = studentRepository.findByAppId(appId);
        if (student == null) {
            model.addAttribute("error", "일치하는 학생 정보를 찾을 수 없어요. QR을 다시 스캔해주세요.");
            return "/attendance/book-confirm";
        }
        // 이 기기의 센터 학생만 — 등록된 기기라는 것만으로 남의 센터 학생을 입실시킬 수는 없다
        kioskService.assertSameCenter(request, student.getCenterCode());

        ClinicRespDTO.BookStatusRespDTO homeState = clinicService.getHomeState(student.getStudentId());
        model.addAttribute("studentName", student.getStudentName());
        model.addAttribute("book", homeState.getBook());
        return "/attendance/book-confirm";
    }
}
