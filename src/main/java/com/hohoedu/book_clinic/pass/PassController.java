package com.hohoedu.book_clinic.pass;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hohoedu.book_clinic._core.handler.exception.Exception401;
import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic.pass._dto.PassRespDTO;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 이용권 조회 API — 학부모 앱 홈 화면의 "남은 횟수" 표시용.
 *
 * 차감(consume)은 여기 없다. 횟수를 까는 것은 출석 처리의 일부라 학생 입실 흐름에서
 * 서버가 알아서 하는 일이지, 클라이언트가 호출할 수 있는 동작이 아니다.
 * 외부에서 부를 수 있게 열어두면 앱에서 남의 횟수를 깎을 수 있게 된다.
 */
@Slf4j
@RestController
@RequestMapping("/pass")
@RequiredArgsConstructor
public class PassController {

    private static final String SESSION_STUDENT_ID = "studentId";

    private final PassService passService;

    /** 잔여 횟수 — 본인 것만 조회할 수 있다 */
    @GetMapping("/remain")
    public ResponseEntity<?> remain(@RequestParam("studentId") String studentId,
                                    @RequestParam(name = "serviceCode", defaultValue = "BOOK") String serviceCode,
                                    HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object sessionStudentId = session == null ? null : session.getAttribute(SESSION_STUDENT_ID);
        if (sessionStudentId == null || !sessionStudentId.equals(studentId)) {
            throw new Exception401("로그인이 필요합니다.");
        }
        return ResponseEntity.ok(ApiUtils.success(
                new PassRespDTO.RemainDTO(serviceCode, passService.remain(studentId, serviceCode))));
    }
}
