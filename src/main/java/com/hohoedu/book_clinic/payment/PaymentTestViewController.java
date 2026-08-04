package com.hohoedu.book_clinic.payment;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 로그인 없이 결제창을 띄우는 개발용 진입점 (dev 프로파일 전용).
 *
 * [왜 필요한가] 정식 경로(/payment/checkout)는 세션이 있어야 열린다. 앱이 없는 동안
 * 결제 흐름만 확인하려면 로그인 없이 결제창을 띄울 수단이 필요해서 둔 것이다.
 *
 * 결제창 화면과 승인·결과 처리는 PaymentCheckoutViewController와 완전히 같은 코드를 쓴다.
 * 테스트에서만 통하는 별도 경로를 만들면 "테스트는 되는데 실제로는 안 되는" 상황이 생긴다.
 *
 * [@Profile("dev")] 운영에는 이 진입점이 뜨지 않는다. 로그인 없이 임의 학생 이름으로
 * 결제를 시작할 수 있는 주소가 운영에 열려 있으면 안 되기 때문이다.
 */
@Slf4j
@Controller
@Profile("dev")
@RequiredArgsConstructor
public class PaymentTestViewController {

    private final PaymentCheckoutViewController checkoutViewController;

    /** 예: /payment/test?studentId=DAE001T01&productCode=BOOK_M8 */
    @GetMapping("/payment/test")
    public String testPage(@RequestParam("studentId") String studentId,
                           @RequestParam(name = "productCode", defaultValue = "BOOK_M8") String productCode,
                           Model model) {
        log.info("[결제테스트] 결제창 진입 — studentId={}, productCode={}", studentId, productCode);
        return checkoutViewController.renderCheckout(studentId, productCode, model);
    }
}
