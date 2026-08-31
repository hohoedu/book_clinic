package com.hohoedu.book_clinic.clinic;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hohoedu.book_clinic._core.handler.exception.Exception401;
import com.hohoedu.book_clinic._core.interceptor.StudentSessionRegistry;
import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic.clinic._dto.ClinicReqDTO;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 학생 독서 클리닉 API — 1단계(책 추천)부터 재설계 (2026-07-09)
 *
 * 인증(2026-07-31): 화면(student-main.js 등)은 페이지에 심어진 studentId를 body에 그대로 실어 보내는데,
 * 이 studentId는 요청 본문 값이라 서버가 검증 없이 그대로 믿으면 curl 등으로 직접 호출 시 다른 학생의
 * studentId를 넣어 그 학생 이름으로 책 추천/채점을 실행시킬 수 있다. StudentViewController.login()이
 * QR 확인 후 심어둔 세션값과 body의 studentId가 같은지 반드시 대조한다.
 */
@Slf4j
@RestController
@RequestMapping("/clinic")
@RequiredArgsConstructor
public class ClinicController {

    private static final String SESSION_STUDENT_ID = "studentId";

    private final ClinicService clinicService;
    private final StudentSessionRegistry studentSessionRegistry;

    /* 요청한 학생 ID가 현재 세션의 학생 ID와 일치하는지 확인 */
    private void requireOwnStudent(HttpServletRequest request, String requestedStudentId) {
        HttpSession session = request.getSession(false);
        Object sessionStudentId = session == null ? null : session.getAttribute(SESSION_STUDENT_ID);
        if (sessionStudentId == null || !sessionStudentId.equals(requestedStudentId)
                || !studentSessionRegistry.isActive((String) sessionStudentId, session.getId())) {
            throw new Exception401("로그인이 필요합니다.");
        }
    }

    /**
     * 홈 화면(student-main) 진입 시 상태 조회 — 입실 처리 + "지금 보여줄 책"(읽던 중인 책 또는
     * 방금 끝낸 책)을 반환한다. 다음 책 추천은 여기서 하지 않는다(recommend 참고).
     */
    @PostMapping("/home-state")
    public ResponseEntity<?> homeState(@RequestBody @Valid ClinicReqDTO.RecommendReqDTO reqDTO,
            HttpServletRequest request) {
        requireOwnStudent(request, reqDTO.getStudentId());
        return ResponseEntity.ok(ApiUtils.success(clinicService.getHomeState(reqDTO.getStudentId())));
    }

    /**
     * 다음 책 추천 — "책 추천받기" 버튼 클릭 시에만 호출된다. 멱등 처리: 이미 추천받은(미해결) 책이
     * 있으면 그 책 그대로, 없으면 새로 추천해서 대여까지 확정한다.
     * 개인 폰 앱 경로라 심화 게이트를 적용한다(enforceAdvancedGate=true, 2026-08-31).
     */
    @PostMapping("/recommend")
    public ResponseEntity<?> recommend(@RequestBody @Valid ClinicReqDTO.RecommendReqDTO reqDTO,
            HttpServletRequest request) {
        requireOwnStudent(request, reqDTO.getStudentId());
        return ResponseEntity.ok(ApiUtils.success(clinicService.recommendBook(reqDTO.getStudentId(), true)));
    }

    /**
     * 문제풀이 기기(학생 개인 앱) 홈 화면 상태 조회 — 입실 처리/책 추천 없이, 이미 확정된 PENDING
     * 추천이 있을 때만 그 책을 보여준다. 입실과 다음 책 추천은 출석체크 기기(/attendance/enter)에서만
     * 일어난다.
     */
    @PostMapping("/quiz-home-state")
    public ResponseEntity<?> quizHomeState(@RequestBody @Valid ClinicReqDTO.RecommendReqDTO reqDTO,
            HttpServletRequest request) {
        requireOwnStudent(request, reqDTO.getStudentId());
        return ResponseEntity.ok(ApiUtils.success(clinicService.getQuizHomeState(reqDTO.getStudentId())));
    }

    /**
     * 특정 책(contentId)의 정보만 읽어온다 — 추천/대여 확정 등 부작용이 전혀 없는 순수 조회.
     * 문제풀이 화면(student-question)이 사이드바에 표시할 책 정보를 가져올 때 쓴다. 예전엔
     * /clinic/recommend(추천/대여 확정 API)를 재사용했는데, "틀린 문제 다시 풀기"로 이미 DONE
     * 처리된 책을 다시 열면 PENDING 추천이 없어 그 API가 엉뚱하게 다음 책을 새로 추천/대여해버리는
     * 부작용이 있었다(2026-08-25 발견).
     */
    @GetMapping("/book-info")
    public ResponseEntity<?> bookInfo(@RequestParam("contentId") Integer contentId) {
        return ResponseEntity.ok(ApiUtils.success(clinicService.getBookInfo(contentId)));
    }

    /**
     * 완독(KING/FRIEND/심화완료) 후 홈의 "완료 화면"에 보여줄 상태 — 남은 액션(틀린 문제 다시 풀기/
     * 심화 문제 풀기) 유무와 책 정보.
     */
    @GetMapping("/completion-state")
    public ResponseEntity<?> completionState(@RequestParam("studentId") String studentId,
            @RequestParam("contentId") Integer contentId, HttpServletRequest request) {
        requireOwnStudent(request, studentId);
        return ResponseEntity.ok(ApiUtils.success(clinicService.getCompletionState(studentId, contentId)));
    }

    /**
     * 재도전(불합격) 중 제출 없이 "나가기"로 결과 화면에 왔을 때 보여줄 직전 제출 결과 — 새로 채점하지
     * 않고 recommend_log에 남은 마지막 기록을 그대로 조회한다.
     */
    @GetMapping("/last-result")
    public ResponseEntity<?> lastResult(@RequestParam("studentId") String studentId,
            @RequestParam("contentId") Integer contentId,
            @RequestParam(value = "qlevel", required = false, defaultValue = "01") String qlevel,
            HttpServletRequest request) {
        requireOwnStudent(request, studentId);
        return ResponseEntity.ok(ApiUtils.success(clinicService.getLastResult(studentId, contentId, qlevel)));
    }

    /**
     * 문제풀이 채점 제출 — qlevel=01(기본)은 합격선(2/3) 이상이면 완독 처리 + 레벨 재계산(완독 권수 기준), 미달이면 재도전.
     * qlevel=02(심화)는 완독/레벨 처리 없이 채점 결과와 풀이 이력 기록만 한다.
     * 정답 수는 클라이언트가 아니라 서버가 문항별 제출 답안(answers)을 itempool.ans와 대조해 직접 계산한다.
     */
    @PostMapping("/quiz/submit")
    public ResponseEntity<?> submitQuiz(@RequestBody @Valid ClinicReqDTO.QuizSubmitReqDTO reqDTO,
            HttpServletRequest request) {
        requireOwnStudent(request, reqDTO.getStudentId());
        return ResponseEntity.ok(ApiUtils.success(clinicService.submitQuiz(
                reqDTO.getStudentId(), reqDTO.getContentId(), reqDTO.getQlevel(), reqDTO.getMode(), reqDTO.getAnswers())));
    }

}
