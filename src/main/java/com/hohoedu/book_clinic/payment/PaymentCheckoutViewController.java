package com.hohoedu.book_clinic.payment;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
import com.hohoedu.book_clinic.payment._dto.PaymentReqDTO;
import com.hohoedu.book_clinic.payment._dto.PaymentRespDTO;
import com.hohoedu.book_clinic.student.StudentRepository;
import com.hohoedu.book_clinic.student.model.Student;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제창 화면 — 앱이 WebView로 띄우는 실제 결제 경로.
 *
 * [왜 화면이 서버에 있나] 결제창을 여는 데 필요한 signature/verification/mKey는 signKey로
 * 만드는 값이라 앱에서 만들 수 없다. 앱에 signKey를 넣으면 디컴파일 한 번으로 유출되고,
 * 그러면 누구나 임의 금액으로 우리 상점의 결제창을 띄울 수 있다. 그래서 결제창을 여는
 * 페이지 자체를 서버가 만들어 내려주고 앱은 그 주소를 WebView로 열기만 한다.
 *
 * [앱이 결과를 아는 방법] 승인이 끝나면 /payment/done?status=... 으로 리다이렉트한다.
 * 앱은 WebView의 URL 변화를 감시하다가 이 주소가 나오면 WebView를 닫고 결과를 읽으면 된다.
 * 화면 내용을 파싱할 필요가 없도록 상태를 전부 쿼리 파라미터로 싣는다.
 */
@Slf4j
@Controller
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentCheckoutViewController {

    private static final String SESSION_STUDENT_ID = "studentId";

    /**
     * 이니시스 모바일 결제창.
     *
     * [PC 웹표준을 쓰지 않는 이유] PC용 INIStdPay.js는 window.open으로 결제창을 띄우는데,
     * webview_flutter는 팝업(onCreateWindow)을 지원하지 않아 앱에서 아무 반응 없이 멈춘다.
     * 모바일 결제창은 같은 창에서 폼을 전송하는 방식이라 WebView에서 정상 동작하고,
     * PC 브라우저로 열어도 그대로 열린다. 그래서 한 경로로 통일했다.
     *
     * 테스트/운영 호스트가 나뉘지 않고 MID로 구분한다.
     */
    static final String MOBILE_PAY_URL = "https://mobile.inicis.com/smart/payment/";

    private final PaymentService paymentService;
    private final InicisProperties props;
    private final StudentRepository studentRepository;

    /**
     * 결제창 — 로그인한 학생(=학부모가 로그인한 자녀 계정) 기준으로 주문을 만들고 결제창을 연다.
     * 앱은 로그인 시 받은 세션 쿠키를 WebView에 심어둔 뒤 이 주소를 열면 된다.
     */
    @GetMapping("/checkout")
    public String checkout(@RequestParam(name = "productCode", required = false) String productCode,
                           @RequestParam(name = "orderNo", required = false) String orderNo,
                           HttpServletRequest request, Model model) {
        HttpSession session = request.getSession(false);
        Object studentId = session == null ? null : session.getAttribute(SESSION_STUDENT_ID);
        if (studentId == null) {
            throw new Exception401("로그인이 필요합니다.");
        }
        // orderNo가 이미 있으면(앱이 /payment/prepare로 먼저 발급받아 온 경우) 그 READY 주문을
        // 재사용한다. 여기서 또 prepare()를 부르면 결제창을 열 때마다 READY 행이 이중으로 생긴다.
        if (orderNo != null && !orderNo.isBlank()) {
            return renderExistingCheckout((String) studentId, orderNo, model);
        }
        if (productCode == null || productCode.isBlank()) {
            throw new Exception400("상품 또는 주문번호가 필요합니다.");
        }
        // 형제가 있으면 결제창을 바로 열지 않고 형제 선택 화면부터 보여준다. 앱은 이 주소를
        // 그대로 부르기만 하면 되므로, 형제 유무 판단은 여기서 서버가 대신 해준다.
        if (studentRepository.findSiblingGroup((String) studentId).size() > 1) {
            return "redirect:/payment/checkout/select?productCode=" + productCode;
        }
        return renderCheckout((String) studentId, productCode, model);
    }

    /**
     * 형제 선택 화면 — 로그인 학생에게 형제가 있으면 본인+형제 목록을 보여주고 몇 명 결제할지
     * 고르게 한다. 형제가 없으면 기존 단일결제 흐름(/checkout)으로 그대로 넘긴다(회귀 없음).
     */
    @GetMapping("/checkout/select")
    public String select(@RequestParam(name = "productCode", required = true) String productCode,
                         HttpServletRequest request, Model model) {
        HttpSession session = request.getSession(false);
        Object studentId = session == null ? null : session.getAttribute(SESSION_STUDENT_ID);
        if (studentId == null) {
            throw new Exception401("로그인이 필요합니다.");
        }

        List<Student> siblings = studentRepository.findSiblingGroup((String) studentId);
        if (siblings.size() <= 1) {
            return "redirect:/payment/checkout?productCode=" + productCode;
        }

        PaymentRespDTO.ProductDTO product = paymentService.productByCode(productCode);
        if (product == null) {
            throw new Exception404("판매 중인 상품이 아닙니다.");
        }

        model.addAttribute("siblings", siblings);
        model.addAttribute("productCode", productCode);
        model.addAttribute("productName", product.getProductName());
        model.addAttribute("price", product.getPrice());
        model.addAttribute("selfStudentId", studentId);
        return "payment/payment-sibling-select";
    }

    /**
     * 형제 묶음결제 시작 — 선택된 학생들의 상품 금액을 합산해 그룹 주문을 만들고
     * 기존 결제창(payment-checkout.html)을 재사용해 렌더링한다.
     */
    @PostMapping("/checkout/group")
    public String group(@RequestParam("productCode") String productCode,
                        @RequestParam("studentIds") List<String> studentIds,
                        HttpServletRequest request, Model model) {
        HttpSession session = request.getSession(false);
        Object studentId = session == null ? null : session.getAttribute(SESSION_STUDENT_ID);
        if (studentId == null) {
            throw new Exception401("로그인이 필요합니다.");
        }

        // 남에게 이용권을 지급하는 경로가 되지 않도록, 선택된 학생이 실제로 로그인 학생의
        // 형제 그룹에 속하는지 다시 확인한다 — 화면에서 이미 걸러 보내지만 요청은 조작될 수 있다.
        List<Student> siblingGroup = studentRepository.findSiblingGroup((String) studentId);
        Set<String> validStudentIds = siblingGroup.stream()
                .map(Student::getStudentId)
                .collect(Collectors.toSet());
        if (!validStudentIds.containsAll(studentIds)) {
            throw new Exception400("형제 관계가 아닌 학생이 포함되어 있습니다.");
        }

        PaymentRespDTO.PrepareGroupDTO prepared = paymentService.prepareGroup(studentIds, productCode);

        String timestamp = String.valueOf(System.currentTimeMillis());
        model.addAttribute("mobilePayUrl", MOBILE_PAY_URL);
        model.addAttribute("mid", prepared.getMid());
        model.addAttribute("oid", prepared.getGroupOrderNo());
        model.addAttribute("price", prepared.getAmount());
        model.addAttribute("goodName", prepared.getProductName());
        model.addAttribute("timestamp", timestamp);
        model.addAttribute("returnUrl", prepared.getReturnUrl());
        model.addAttribute("closeUrl", prepared.getCloseUrl() + "?orderNo=" + prepared.getGroupOrderNo());
        model.addAttribute("studentId", studentId);
        model.addAttribute("testMode", prepared.isTestMode());
        return "payment/payment-checkout";
    }

    /**
     * 이미 발급된 READY 주문으로 결제창을 연다. 단일결제 orderNo든 형제 묶음결제의
     * group_order_no든 구분 없이 받는다 — 앱은 prepare()/prepare/group() 중 무엇을
     * 불렀는지와 무관하게 받은 값을 그대로 이 orderNo 파라미터로 넘기면 된다.
     *
     * 앱이 X 버튼을 눌렀을 때 "어느 주문을 버릴지" 알아야 /payment/abandon을 부를 수 있어서,
     * 결제창을 열기 전에 앱이 먼저 orderNo를 알 수 있는 경로가 필요해 추가했다.
     */
    private String renderExistingCheckout(String studentId, String orderNo, Model model) {
        PaymentRespDTO.PrepareGroupDTO prepared = paymentService.findOwnReadyForCheckout(studentId, orderNo);

        String timestamp = String.valueOf(System.currentTimeMillis());
        model.addAttribute("mobilePayUrl", MOBILE_PAY_URL);
        model.addAttribute("mid", prepared.getMid());
        model.addAttribute("oid", prepared.getGroupOrderNo());
        model.addAttribute("price", prepared.getAmount());
        model.addAttribute("goodName", prepared.getProductName());
        model.addAttribute("timestamp", timestamp);
        model.addAttribute("returnUrl", prepared.getReturnUrl());
        model.addAttribute("studentId", studentId);
        model.addAttribute("testMode", prepared.isTestMode());
        return "payment/payment-checkout";
    }

    /**
     * 결제창 파라미터를 만들어 화면에 심는다. 개발용 테스트 화면도 같은 로직을 쓰도록 공유한다.
     */
    String renderCheckout(String studentId, String productCode, Model model) {
        PaymentReqDTO.PrepareDTO prepareReq = new PaymentReqDTO.PrepareDTO();
        prepareReq.setStudentId(studentId);
        prepareReq.setProductCode(productCode);

        PaymentRespDTO.PrepareDTO prepared = paymentService.prepare(prepareReq);

        // 결제창 서명은 승인 서명과 재료가 다르다(authToken이 아직 없으므로 주문번호·금액·시각으로 만든다)
        String timestamp = String.valueOf(System.currentTimeMillis());

        model.addAttribute("mobilePayUrl", MOBILE_PAY_URL);
        model.addAttribute("mid", prepared.getMid());
        model.addAttribute("oid", prepared.getOrderNo());
        model.addAttribute("price", prepared.getAmount());
        model.addAttribute("goodName", prepared.getProductName());
        model.addAttribute("timestamp", timestamp);
        model.addAttribute("returnUrl", props.getReturnUrl());
        // 결제창이 닫힐 때 "어느 주문이 취소됐는지" 알아야 READY 행을 정리할 수 있다.
        // closeUrl은 우리가 지정하는 값이라 주문번호를 쿼리로 실어 보낸다.
        model.addAttribute("closeUrl", props.getCloseUrl() + "?orderNo=" + prepared.getOrderNo());
        model.addAttribute("studentId", studentId);
        model.addAttribute("testMode", props.isTestMode());

        return "payment/payment-checkout";
    }

    /**
     * 결제창 인증 결과 수신 → 서버 승인 → 결과 주소로 리다이렉트.
     *
     * 이니시스 도메인에서 출발하는 cross-site POST라 세션 쿠키가 딸려오지 않는다.
     * 그래서 세션이 아니라 주문번호로 결제 행을 찾아 승인한다(approveByReturn).
     */
    @PostMapping("/return")
    public String returnUrl(@RequestParam Map<String, String> params) {
        // 모바일과 PC는 응답 파라미터 이름이 완전히 다르다(P_STATUS vs resultCode).
        // 지금 결제창은 모바일 하나지만, 나중에 PC를 다시 붙여도 이 주소 하나로 받게 분기해 둔다.
        boolean mobile = params.containsKey("P_STATUS");
        log.info("[결제] 인증 결과 수신 — mobile={}, params={}", mobile, params);

        try {
            if (mobile) {
                if (!"00".equals(params.get("P_STATUS"))) {
                    // 실패든 사용자가 결제 페이지 안에서 취소했든, READY로 방치하면 안 된다.
                    // P_NOTI가 우리 주문번호다(인증 응답에는 P_OID가 없어 이 값으로만 식별된다).
                    String orderNo = params.get("P_NOTI");
                    if (orderNo != null && !orderNo.isBlank()) {
                        paymentService.abandon(orderNo);
                    }
                    return done("fail", null, 0, params.get("P_RMESG1"));
                }
                // 인증 응답에는 P_OID가 없다 — P_NOTI로 우리가 실어 보낸 주문번호를 되돌려받는다.
                PaymentRespDTO.ApproveDTO approved = paymentService.approveMobileByReturn(
                        params.get("P_NOTI"), params.get("P_REQ_URL"),
                        params.get("P_TID"), params.get("P_AMT"));
                return done("ok", approved.getPaymentId(), approved.getRemainCount(), null);
            }

            if (!"0000".equals(params.get("resultCode"))) {
                return done("fail", null, 0, params.get("resultMsg"));
            }
            PaymentRespDTO.ApproveDTO approved = paymentService.approveByReturn(
                    params.get("orderNumber"), params.get("authToken"),
                    params.get("authUrl"), params.get("netCancelUrl"));
            return done("ok", approved.getPaymentId(), approved.getRemainCount(), null);
        } catch (Exception e) {
            log.error("[결제] 승인 실패", e);
            return done("fail", null, 0, e.getMessage());
        }
    }

    /**
     * 사용자가 결제창을 닫았을 때 — 승인까지 못 간 READY 주문을 정리한다.
     *
     * 정리하지 않으면 "결제창만 띄우고 만" 행이 READY로 계속 쌓여, 나중에 정산 대조 때
     * 이탈 건인지 처리 중인 건인지 구분이 안 된다. 이니시스가 GET으로 부르는 경우도 있어
     * 두 메서드를 다 받는다.
     */
    @RequestMapping(path = "/close", method = { RequestMethod.GET, RequestMethod.POST })
    public String closeUrl(@RequestParam(name = "orderNo", required = false) String orderNo) {
        if (orderNo != null && !orderNo.isBlank()) {
            paymentService.abandon(orderNo);
        }
        return done("cancel", null, 0, null);
    }

    /**
     * 결제 결과 화면 — 앱이 이 주소를 감지해 WebView를 닫는다.
     * 사람이 브라우저로 결제했을 때를 위해 화면도 함께 그린다.
     */
    @GetMapping("/done")
    public String donePage(@RequestParam(name = "status", defaultValue = "fail") String status,
                           @RequestParam(name = "paymentId", required = false) Integer paymentId,
                           @RequestParam(name = "remain", defaultValue = "0") int remain,
                           @RequestParam(name = "msg", required = false) String msg,
                           Model model) {
        model.addAttribute("status", status);
        model.addAttribute("paymentId", paymentId);
        model.addAttribute("remain", remain);
        model.addAttribute("msg", msg);
        return "payment/payment-done";
    }

    private String done(String status, Integer paymentId, int remain, String msg) {
        StringBuilder url = new StringBuilder("redirect:/payment/done?status=").append(status);
        if (paymentId != null) {
            url.append("&paymentId=").append(paymentId);
        }
        url.append("&remain=").append(remain);
        if (msg != null && !msg.isBlank()) {
            url.append("&msg=").append(URLEncoder.encode(msg, StandardCharsets.UTF_8));
        }
        return url.toString();
    }
}
