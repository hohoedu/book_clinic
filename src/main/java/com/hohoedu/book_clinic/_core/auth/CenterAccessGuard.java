package com.hohoedu.book_clinic._core.auth;

import org.springframework.stereotype.Component;

import com.hohoedu.book_clinic._core.handler.exception.Exception401;
import com.hohoedu.book_clinic._core.handler.exception.Exception403;
import com.hohoedu.book_clinic._core.handler.exception.Exception404;
import com.hohoedu.book_clinic.student.StudentRepository;
import com.hohoedu.book_clinic.student.model.Student;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 API의 센터 스코핑 가드 (2026-08-20).
 *
 * [왜 필요한가] 조회 API들은 로그인한 직원의 centerCode로 쿼리를 걸어 자연스럽게 스코핑되지만,
 * 대상 학생을 요청 본문(studentId)으로 직접 지정하는 처리 API들은 그 값을 그대로 믿으면
 * 다른 센터 학생을 예약·취소·퇴실시킬 수 있다(스트레스 테스트에서 DAE001 관리자 세션으로
 * PUS002 학생 예약 생성/취소/강제퇴실이 전부 성공하는 것을 확인). 요청 본문은 조작될 수 있으므로
 * "이 학생이 정말 내 센터 소속인지"를 서버가 매번 다시 확인해야 한다.
 *
 * [왜 컨트롤러마다 private 헬퍼가 아닌 공용 빈인가] 같은 검증이 필요한 엔드포인트가
 * ReservationAdminController와 MonitorController 양쪽에 흩어져 있어, 각자 복사해두면 한쪽만
 * 고쳐지는 상황이 다시 생긴다. 검증 규칙을 한 곳에 둔다.
 */
@Component
@RequiredArgsConstructor
public class CenterAccessGuard {

    private final StudentRepository studentRepository;

    /** 로그인한 직원의 소속 센터. 세션이 없거나 센터가 비어 있으면 401 */
    public String requireCenterCode(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getLoginUser() == null
                || userDetails.getLoginUser().getCenterCode() == null) {
            throw new Exception401("로그인이 필요합니다.");
        }
        return userDetails.getLoginUser().getCenterCode();
    }

    /**
     * 요청 대상 학생이 로그인한 직원의 센터 소속인지 확인하고, 확인된 센터코드를 돌려준다.
     *
     * 없는 학생은 404, 다른 센터 학생은 403으로 나눈다 — 둘 다 막히는 건 같지만, 화면에서
     * "학생을 찾을 수 없다"와 "우리 센터 학생이 아니다"는 직원이 취할 조치가 다르다.
     */
    public String requireStudentInMyCenter(CustomUserDetails userDetails, String studentId) {
        String centerCode = requireCenterCode(userDetails);
        if (studentId == null || studentId.isBlank()) {
            throw new Exception404("학생 정보를 찾을 수 없습니다.");
        }
        Student student = studentRepository.findById(studentId);
        if (student == null) {
            throw new Exception404("학생 정보를 찾을 수 없습니다: studentId=" + studentId);
        }
        if (!centerCode.equals(student.getCenterCode())) {
            throw new Exception403("다른 센터 학생은 처리할 수 없습니다.");
        }
        return centerCode;
    }
}
