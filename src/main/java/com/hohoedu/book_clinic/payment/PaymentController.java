package com.hohoedu.book_clinic.payment;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.handler.exception.Exception401;
import com.hohoedu.book_clinic._core.utils.ApiUtils;
import com.hohoedu.book_clinic.payment._dto.PaymentReqDTO;
import com.hohoedu.book_clinic.student.StudentRepository;
import com.hohoedu.book_clinic.student.model.Student;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제/환불 API (KG이니시스).
 *
 * [인증] ClinicController와 같은 방식이다. 화면·앱은 body에 studentId를 실어 보내는데
 * 그 값을 검증 없이 믿으면 다른 학생의 결제 내역을 조회하거나 환불을 실행시킬 수 있다.
 * 로그인 시 세션에 심어둔 studentId와 반드시 대조한다.
 *
 * [금액] 어떤 엔드포인트도 금액을 입력으로 받지 않는다. 결제 금액은 상품 마스터에서,
 * 환불 금액은 환불 규정에서 서버가 계산한다.
 */
@Slf4j
@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private static final String SESSION_STUDENT_ID = "studentId";

    private final PaymentService paymentService;
    private final StudentRepository studentRepository;

    private void requireOwnStudent(HttpServletRequest request, String requestedStudentId) {
        HttpSession session = request.getSession(false);
        Object sessionStudentId = session == null ? null : session.getAttribute(SESSION_STUDENT_ID);
        if (sessionStudentId == null || !sessionStudentId.equals(requestedStudentId)) {
            throw new Exception401("로그인이 필요합니다.");
        }
    }

    /**
     * 판매중인 상품 목록. 가격표는 공개 정보라 로그인 없이도 볼 수 있게 둔다
     * (앱이 로그인 화면에서도 요금제를 보여줄 수 있어야 한다).
     */
    @GetMapping("/products")
    public ResponseEntity<?> products(@RequestParam(name = "serviceCode", required = false) String serviceCode) {
        return ResponseEntity.ok(ApiUtils.success(paymentService.products(serviceCode)));
    }

    /** 결제 시작 — 주문번호를 발급받아 앱이 이니시스 결제창을 띄운다 */
    @PostMapping("/prepare")
    public ResponseEntity<?> prepare(@Valid @RequestBody PaymentReqDTO.PrepareDTO reqDTO,
                                     HttpServletRequest request) {
        requireOwnStudent(request, reqDTO.getStudentId());
        return ResponseEntity.ok(ApiUtils.success(paymentService.prepare(reqDTO)));
    }

    /**
     * 형제 목록 조회 — 앱이 결제 시작 전에 이걸 불러 형제가 있으면 선택 UI를 보여주고,
     * 없으면(본인 1건만 돌아오면) 기존처럼 바로 prepare()를 부르면 된다.
     */
    @GetMapping("/siblings")
    public ResponseEntity<?> siblings(@RequestParam("studentId") String studentId, HttpServletRequest request) {
        requireOwnStudent(request, studentId);
        List<Student> siblings = studentRepository.findSiblingGroup(studentId);
        return ResponseEntity.ok(ApiUtils.success(siblings));
    }

    /**
     * 형제 묶음결제 시작 — prepare()의 그룹 버전. 선택된 학생들의 상품 금액을 합산해
     * 그룹 주문번호를 발급한다. 앱은 이 orderNo로 그대로 결제창(WebView)을 열면 된다
     * (결제창/승인/이탈 경로는 단일결제와 동일한 orderNo 파라미터를 그대로 쓴다).
     */
    @PostMapping("/prepare/group")
    public ResponseEntity<?> prepareGroup(@Valid @RequestBody PaymentReqDTO.PrepareGroupDTO reqDTO,
                                          HttpServletRequest request) {
        requireOwnStudent(request, reqDTO.getStudentId());

        List<String> siblingStudentIds = reqDTO.getSiblingStudentIds();
        if (siblingStudentIds == null || siblingStudentIds.isEmpty()) {
            throw new Exception400("결제할 학생을 선택해주세요.");
        }
        // 남에게 이용권을 지급하는 경로가 되지 않도록, 선택된 학생이 실제로 요청자의
        // 형제 그룹에 속하는지 서버가 다시 확인한다 — 요청은 조작될 수 있다.
        Set<String> validStudentIds = studentRepository.findSiblingGroup(reqDTO.getStudentId()).stream()
                .map(Student::getStudentId)
                .collect(Collectors.toSet());
        if (!validStudentIds.containsAll(siblingStudentIds)) {
            throw new Exception400("형제 관계가 아닌 학생이 포함되어 있습니다.");
        }

        return ResponseEntity.ok(ApiUtils.success(
                paymentService.prepareGroup(siblingStudentIds, reqDTO.getProductCode())));
    }

    /** 승인 — 결제창 인증 결과를 넘기면 서버가 이니시스에 승인을 요청하고 이용권을 발급한다 */
    @PostMapping("/approve")
    public ResponseEntity<?> approve(@Valid @RequestBody PaymentReqDTO.ApproveDTO reqDTO,
                                     HttpServletRequest request) {
        requireOwnStudent(request, reqDTO.getStudentId());
        return ResponseEntity.ok(ApiUtils.success(paymentService.approve(reqDTO)));
    }

    /**
     * 결제 포기 — 앱에서 결제창을 닫을 때(X 버튼) 호출한다.
     * READY로 방치되면 정산 대조 때 이탈 건인지 진행 중인 건인지 구분이 안 되므로 즉시 정리한다.
     * 본인 것이 아니거나 이미 끝난 주문이면 서비스가 조용히 무시한다(앱 종료 타이밍 경합으로 흔함).
     */
    @PostMapping("/abandon")
    public ResponseEntity<?> abandon(@Valid @RequestBody PaymentReqDTO.AbandonDTO reqDTO,
                                     HttpServletRequest request) {
        requireOwnStudent(request, reqDTO.getStudentId());
        paymentService.abandon(reqDTO.getStudentId(), reqDTO.getOrderNo());
        return ResponseEntity.ok(ApiUtils.success("ok"));
    }

    /**
     * 환불 견적 — 실제 환불 전에 "며칠 지났고 몇 회 썼으니 얼마"를 미리 보여준다.
     * 실행과 같은 계산기를 쓰므로 여기 나온 금액과 실제 환불액은 항상 같다.
     */
    @GetMapping("/refund/quote")
    public ResponseEntity<?> quote(@RequestParam("studentId") String studentId,
                                   @RequestParam("paymentId") int paymentId,
                                   HttpServletRequest request) {
        requireOwnStudent(request, studentId);
        return ResponseEntity.ok(ApiUtils.success(paymentService.quote(studentId, paymentId)));
    }

    /** 환불 실행 — 규정이 자동 적용된다. 앱은 금액을 정하지 않는다 */
    @PostMapping("/refund")
    public ResponseEntity<?> refund(@Valid @RequestBody PaymentReqDTO.RefundDTO reqDTO,
                                    HttpServletRequest request) {
        requireOwnStudent(request, reqDTO.getStudentId());
        return ResponseEntity.ok(ApiUtils.success(
                paymentService.refund(reqDTO.getStudentId(), reqDTO.getPaymentId(), reqDTO.getReason(), "APP")));
    }

    /** 결제 내역 — 로그인 학생 본인 것만 (형제 것은 여기 안 나온다) */
    @GetMapping("/history")
    public ResponseEntity<?> history(@RequestParam("studentId") String studentId, HttpServletRequest request) {
        requireOwnStudent(request, studentId);
        return ResponseEntity.ok(ApiUtils.success(paymentService.history(studentId)));
    }

    /**
     * 형제 묶음결제 그룹원 조회 — 결제 내역에서 그룹 결제 건의 "환불"을 누르면, 결제할 때와
     * 똑같은 체크박스 화면으로 "이 그룹 중 누구를 환불할지" 고를 수 있어야 한다. 그 체크박스
     * 목록을 채우기 위해 그룹 전체(본인 포함)를 학생명과 함께 돌려준다.
     */
    @GetMapping("/refund/group-members")
    public ResponseEntity<?> refundGroupMembers(@RequestParam("studentId") String studentId,
                                                @RequestParam("groupOrderNo") String groupOrderNo,
                                                HttpServletRequest request) {
        requireOwnStudent(request, studentId);
        return ResponseEntity.ok(ApiUtils.success(paymentService.groupMembers(studentId, groupOrderNo)));
    }
}
