package com.hohoedu.book_clinic.clinic;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic.clinic._dto.ClinicReqDTO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 학생 독서 클리닉 API — 1단계(책 추천)부터 재설계 (2026-07-09)
 */
@Slf4j
@RestController
@RequestMapping("/clinic")
@RequiredArgsConstructor
public class ClinicController {

    private final ClinicService clinicService;

    /**
     * 책 확인 — 멱등 처리. 이미 추천받은(미해결) 책이 있으면 그 책 그대로, 없으면 새로 추천해서
     * 대여까지 확정한다. "다른 책 추천" 같은 재추천 액션은 의도적으로 없음.
     */
    @PostMapping("/recommend")
    public ResponseEntity<?> recommend(@RequestBody @Valid ClinicReqDTO.RecommendReqDTO reqDTO) {
        return ResponseEntity.ok(ApiUtils.success(clinicService.recommendBook(reqDTO.getStudentId())));
    }

    /**
     * 기본 문제풀이(qlevel=01) 채점 제출 — 합격선(2/3) 이상이면 완독 처리 + EXP/레벨 갱신, 미달이면 재도전.
     * 정답 수는 클라이언트가 아니라 서버가 문항별 제출 답안(answers)을 itempool.ans와 대조해 직접 계산한다.
     */
    @PostMapping("/quiz/submit")
    public ResponseEntity<?> submitQuiz(@RequestBody @Valid ClinicReqDTO.QuizSubmitReqDTO reqDTO) {
        return ResponseEntity.ok(ApiUtils.success(clinicService.submitQuiz(
                reqDTO.getStudentId(), reqDTO.getContentId(), reqDTO.getAnswers())));
    }

}
