package com.hohoedu.book_clinic.payment;

import java.time.LocalDate;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.view.BillingRegViewController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 로그인 없이 자동결제 카드등록창을 띄우는 개발용 진입점 (dev 프로파일 전용) — 2026-09-09.
 *
 * [왜 필요한가] 정식 경로(/payment/billing/checkout)는 세션이 있어야 열린다. 앱이 아직
 * 이 URL로 붙지 않은 동안 빌키 발급 흐름을 확인하려면 로그인 없이 창을 띄울 수단이 필요하다.
 * 일시불 쪽 PaymentTestViewController(/payment/test)와 같은 목적·같은 구조다.
 *
 * 카드등록창 화면과 결과 처리(/payment/billing/return)는 BillingRegViewController와 완전히
 * 같은 코드를 쓴다 — renderForTest()가 운영 경로의 render()를 그대로 부른다.
 *
 * [@Profile("dev")] 운영에는 뜨지 않는다. 로그인 없이 임의 학생으로 빌키 발급을 시작할 수
 * 있는 주소가 운영에 열려 있으면 안 된다.
 */
@Slf4j
@Controller
@Profile("dev")
@RequiredArgsConstructor
public class BillingRegTestController {

    private final BillingRegViewController billingRegViewController;

    /**
     * 예: /payment/billing/test?studentId=DAE001T01&productCode=BOOK_M8&firstBillingOn=2026-09-15
     *
     * 형제 합산을 테스트하려면 studentIds 를 반복해 넘긴다:
     *   /payment/billing/test?studentId=DAE001T01&studentIds=DAE001T01&studentIds=DAE001T02
     * (studentIds 를 생략하면 studentId 한 명만 등록한다)
     */
    @GetMapping("/payment/billing/test")
    public String testPage(@RequestParam("studentId") String studentId,
                           @RequestParam(name = "productCode", defaultValue = "BOOK_M8") String productCode,
                           @RequestParam(name = "firstBillingOn", required = false) String firstBillingOn,
                           @RequestParam(name = "studentIds", required = false) List<String> studentIds,
                           Model model) {
        List<String> targets = (studentIds == null || studentIds.isEmpty()) ? List.of(studentId) : studentIds;
        LocalDate firstOn = parseDate(firstBillingOn);
        log.info("[자동결제테스트] 카드등록창 진입 — owner={}, targets={}, productCode={}, firstBillingOn={}",
                studentId, targets, productCode, firstOn);
        return billingRegViewController.renderForTest(studentId, targets, productCode, firstOn, model);
    }

    /** "2026-09-15" → LocalDate. 비었으면 null(서버가 등록일로 폴백) */
    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new Exception400("첫 결제일 형식이 올바르지 않습니다: " + raw);
        }
    }
}
