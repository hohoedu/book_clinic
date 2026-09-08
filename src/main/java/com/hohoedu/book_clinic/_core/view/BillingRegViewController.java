package com.hohoedu.book_clinic._core.view;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.handler.exception.Exception401;
import com.hohoedu.book_clinic._core.handler.exception.Exception404;
import com.hohoedu.book_clinic.payment.PaymentService;
import com.hohoedu.book_clinic.payment.SubscriptionService;
import com.hohoedu.book_clinic.payment._dto.PaymentRespDTO;
import com.hohoedu.book_clinic.payment._dto.SubscriptionRespDTO;
import com.hohoedu.book_clinic.payment.inicis.InicisClient;
import com.hohoedu.book_clinic.payment.inicis.InicisProperties;
import com.hohoedu.book_clinic.student.StudentRepository;
import com.hohoedu.book_clinic.student.model.Student;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 자동결제 카드등록 화면 — 앱이 WebView로 띄우는 빌키 발급 경로 (2026-09-07).
 *
 * [일반 결제창과 무엇이 다른가] 이 단계에서는 돈이 빠지지 않는다. 카드 본인확인을 거쳐
 * 빌키를 받아오는 것이 전부이고, 실제 첫 청구는 등록이 확정된 직후 서버가 빌키로 따로 낸다.
 * 요청 URL과 파라미터도 일반 모바일 결제창과 다른 별도 모듈이다(mo-bill).
 *
 * [왜 화면이 서버에 있나] PaymentCheckoutViewController와 같은 이유다 — 상점 정보를 앱에
 * 넣지 않기 위해서다. 앱은 이 주소를 WebView로 열고 결과 주소(/payment/done)만 감시한다.
 */
@Slf4j
@Controller
@RequestMapping("/payment/billing")
@RequiredArgsConstructor
public class BillingRegViewController {

    private static final String SESSION_STUDENT_ID = "studentId";

    private final SubscriptionService subscriptionService;
    private final PaymentService paymentService;
    private final StudentRepository studentRepository;
    private final InicisClient inicisClient;
    private final InicisProperties props;

    /**
     * 카드등록창 — 형제가 있으면 먼저 누구를 등록할지 고르게 하고, 없으면 바로 연다.
     * 형제 판단을 서버가 대신하므로 앱은 이 주소 하나만 알면 된다(기존 결제창과 같은 방식).
     */
    @GetMapping("/checkout")
    public String checkout(@RequestParam("productCode") String productCode,
                           @RequestParam(value = "firstBillingOn", required = false) String firstBillingOn,
                           HttpServletRequest request, Model model) {
        String studentId = requireLogin(request);

        List<Student> siblings = studentRepository.findSiblingGroup(studentId);
        if (siblings.size() > 1) {
            PaymentRespDTO.ProductDTO product = paymentService.productByCode(productCode);
            if (product == null) {
                throw new Exception404("판매 중인 상품이 아닙니다.");
            }
            model.addAttribute("siblings", siblings);
            model.addAttribute("productCode", productCode);
            model.addAttribute("productName", product.getProductName());
            model.addAttribute("price", product.getPrice());
            model.addAttribute("firstBillingOn", firstBillingOn);
            model.addAttribute("selfStudentId", studentId);
            model.addAttribute("formAction", "/payment/billing/checkout/group");
            return "payment/payment-sibling-select";
        }
        return render(studentId, List.of(studentId), productCode, parseDate(firstBillingOn), model);
    }

    /** 형제 합산 카드등록 — 고른 학생들을 한 구독(카드 1장)에 묶는다 */
    @PostMapping("/checkout/group")
    public String checkoutGroup(@RequestParam("productCode") String productCode,
                                @RequestParam("studentIds") List<String> studentIds,
                                @RequestParam(value = "firstBillingOn", required = false) String firstBillingOn,
                                HttpServletRequest request, Model model) {
        String studentId = requireLogin(request);
        if (studentIds == null || studentIds.isEmpty()) {
            throw new Exception400("자동결제를 등록할 학생을 선택해주세요.");
        }
        // 남의 형제를 끼워 넣지 못하게 막는다 — 화면이 보낸 목록을 그대로 믿지 않는다.
        List<String> allowed = studentRepository.findSiblingGroup(studentId).stream()
                .map(Student::getStudentId).toList();
        for (String target : studentIds) {
            if (!allowed.contains(target)) {
                throw new Exception400("형제가 아닌 학생이 포함되어 있습니다.");
            }
        }
        return render(studentId, studentIds, productCode, parseDate(firstBillingOn), model);
    }

    /** "2026-10-15" → LocalDate. 비었거나 형식이 틀리면 null(서버가 등록일로 폴백) */
    private java.time.LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return java.time.LocalDate.parse(raw.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new Exception400("첫 결제일 형식이 올바르지 않습니다: " + raw);
        }
    }

    private static final java.time.format.DateTimeFormatter TS14 =
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private String render(String ownerStudentId, List<String> studentIds, String productCode,
                          java.time.LocalDate firstBillingOn, Model model) {
        SubscriptionRespDTO.CardRegDTO reg =
                subscriptionService.prepareCardReg(ownerStudentId, studentIds, productCode, firstBillingOn);

        // INILite 모바일 빌키발급 창(docs/billing 매뉴얼).
        // [price=0 검증 중] 결제 없이 빌키만 발급되는지 확인한다(2026-09-08). 되면 첫 결제도
        // 학부모가 고른 결제일에 배치가 낸다(E-1). 이니시스가 0을 거부하면 월액으로 되돌려
        // 등록 즉시 첫 결제로 처리한다(E-2).
        String orderId = reg.getOrderNo();
        String price = "0";
        String timestamp = com.hohoedu.book_clinic._core.utils.KstClock.now().format(TS14);
        String hashData = inicisClient.iniLiteBillKeyHash(price, reg.getMid(), orderId, timestamp);

        model.addAttribute("billingUrl", props.getBillingMobileUrl());
        model.addAttribute("mid", reg.getMid());
        model.addAttribute("oid", orderId);
        model.addAttribute("price", price);
        model.addAttribute("timestamp", timestamp);
        model.addAttribute("hashData", hashData);
        model.addAttribute("clientIp", props.getClientIp());
        model.addAttribute("siteUrl", props.getBillingSiteUrl());
        model.addAttribute("goodName", reg.getGoodName());
        model.addAttribute("buyerName", reg.getBuyerName());
        model.addAttribute("returnUrl", reg.getReturnUrl());
        model.addAttribute("closeUrl", reg.getCloseUrl());
        model.addAttribute("monthlyAmount", reg.getMonthlyAmount());
        model.addAttribute("studentCount", studentIds.size());
        model.addAttribute("testMode", reg.isTestMode());
        return "payment/billing-checkout";
    }

    /**
     * 카드등록(빌키발급) 결과 수신 → 빌키 저장 + 첫 청구 확정 → 결과 주소로 리다이렉트.
     *
     * 이니시스 도메인발 cross-site POST라 세션 쿠키가 없다. INILite 빌키발급 창은 우리가 보낸
     * orderId를 그대로 되돌려주므로(P_NOTI 대신) 그 값으로 구독을 되짚는다.
     *
     * INILite 방식은 결제창이 returnUrl로 billkey를 직접 던져준다 — 2차 승인 호출이 없다.
     * price는 발급 시점에 실승인되므로 이 승인 1건이 곧 첫 청구다.
     */
    @PostMapping("/return")
    public String returnUrl(@RequestParam Map<String, String> params) {
        log.info("[자동결제] 카드등록 결과 수신 — keys={}, resultCode={}, resultMessage={}, tid={}",
                params.keySet(), params.get("resultCode"), params.get("resultMessage"), params.get("tid"));

        // ── INILite 빌키발급 응답 (docs/billing/INIbill_mo_return_new.jsp 규격) ──
        // 빌키발급 창은 P_STATUS를 쓰지 않는다. resultCode 또는 billkey 중 하나라도 있으면 이 경로다.
        String resultCode = params.get("resultCode");
        String billKey = firstNonBlank(params.get("billkey"), params.get("billKey"), params.get("BillKey"),
                params.get("BILLKEY"), params.get("P_BILLKEY"), params.get("CARD_BillKey"));
        if (resultCode != null || billKey != null) {
            String regOrderNo = firstNonBlank(params.get("orderId"), params.get("orderNumber"), params.get("P_NOTI"));
            try {
                // 빌키를 받았으면 발급 성공이다(실패 시엔 빌키가 나오지 않는다). resultCode는 기록만 한다.
                if (billKey == null || billKey.isBlank()) {
                    log.warn("[자동결제] 빌키발급 실패 — resultCode={}, msg={}",
                            resultCode, params.get("resultMessage"));
                    if (regOrderNo != null && !regOrderNo.isBlank()) {
                        subscriptionService.abandonCardReg(regOrderNo);
                    }
                    return done("fail", 0, nvl(params.get("resultMessage"), "카드 등록이 승인되지 않았습니다."));
                }
                log.info("[자동결제] 빌키발급 성공 — resultCode={}, orderId={}, tid={}",
                        resultCode, regOrderNo, params.get("tid"));
                String cardName = firstNonBlank(params.get("cardCompanyName"), params.get("cardName"),
                        params.get("cardKindName"), params.get("cardTypeName"));
                String cardNo = firstNonBlank(params.get("cardNumber"), params.get("cardNo"));
                SubscriptionRespDTO.CardRegResultDTO result = subscriptionService.completeBillKeyReg(
                        regOrderNo, billKey, cardName, cardNo);
                String status = "ACTIVE".equals(result.getStatus()) ? "ok" : "billing_fail";
                return done(status, result.getRemainCount(), null);
            } catch (Exception e) {
                log.error("[자동결제] 카드등록 실패 — orderId={}", regOrderNo, e);
                return done("fail", 0, e.getMessage());
            }
        }

        // ── (fallback) 옛 모바일 결제창 응답 (P_STATUS + P_REQ_URL 2차 승인) ──
        String regOrderNo = params.get("P_NOTI");
        try {
            if (!"00".equals(params.get("P_STATUS"))) {
                if (regOrderNo != null && !regOrderNo.isBlank()) {
                    subscriptionService.abandonCardReg(regOrderNo);
                }
                return done("fail", 0, params.get("P_RMESG1"));
            }
            SubscriptionRespDTO.CardRegResultDTO result = subscriptionService.completeCardReg(
                    regOrderNo, params.get("P_REQ_URL"), params.get("P_TID"));
            String status = "ACTIVE".equals(result.getStatus()) ? "ok" : "billing_fail";
            return done(status, result.getRemainCount(), null);
        } catch (Exception e) {
            log.error("[자동결제] 카드등록 실패 — regOrderNo={}", regOrderNo, e);
            return done("fail", 0, e.getMessage());
        }
    }

    private String firstNonBlank(String... vs) {
        for (String v : vs) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private String nvl(String v, String fallback) {
        return (v == null || v.isBlank()) ? fallback : v;
    }

    /** 사용자가 카드등록창을 닫았을 때 — PENDING 구독을 정리한다 */
    @RequestMapping(path = "/close", method = { RequestMethod.GET, RequestMethod.POST })
    public String closeUrl(@RequestParam(name = "regOrderNo", required = false) String regOrderNo) {
        if (regOrderNo != null && !regOrderNo.isBlank()) {
            subscriptionService.abandonCardReg(regOrderNo);
        }
        return done("cancel", 0, null);
    }

    /** 결과 화면은 일반 결제와 공유한다 — 앱이 감시하는 주소를 하나로 유지하기 위해서다 */
    private String done(String status, int remain, String msg) {
        StringBuilder url = new StringBuilder("redirect:/payment/done?status=").append(status)
                .append("&remain=").append(remain);
        if (msg != null && !msg.isBlank()) {
            url.append("&msg=").append(URLEncoder.encode(msg, StandardCharsets.UTF_8));
        }
        return url.toString();
    }

    private String requireLogin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object studentId = session == null ? null : session.getAttribute(SESSION_STUDENT_ID);
        if (studentId == null) {
            throw new Exception401("로그인이 필요합니다.");
        }
        return (String) studentId;
    }
}
