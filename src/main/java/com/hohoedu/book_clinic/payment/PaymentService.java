package com.hohoedu.book_clinic.payment;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.handler.exception.Exception404;
import com.hohoedu.book_clinic._core.handler.exception.Exception500;
import com.hohoedu.book_clinic._core.utils.KstClock;
import com.hohoedu.book_clinic.clinic.ClinicRepository;
import com.hohoedu.book_clinic.pass.PassService;
import com.hohoedu.book_clinic.pass._dto.PassRespDTO;
import com.hohoedu.book_clinic.payment._dto.PaymentReqDTO;
import com.hohoedu.book_clinic.payment._dto.PaymentRespDTO;
import com.hohoedu.book_clinic.student.StudentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제/환불 비즈니스 로직 (KG이니시스).
 *
 * [이 클래스가 지키는 원칙]
 * 1. 금액은 언제나 서버가 정한다. 앱이 보낸 금액은 받지도 않는다.
 * 2. 승인 응답의 금액이 우리가 기록한 금액과 다르면 즉시 망취소하고 실패로 끝낸다.
 *    이 검증이 없으면 결제창을 조작해 100원으로 결제하고 이용권을 받아갈 수 있다.
 * 3. 승인 이후 어떤 이유로든 확정에 실패하면 반드시 망취소한다. 그냥 예외를 던지면
 *    고객 카드에는 승인이 남고 우리 DB에는 결제가 없는 상태가 된다.
 * 4. PG를 부른 모든 호출은 성공/실패와 무관하게 원문을 로그 테이블에 남긴다.
 *
 * [트랜잭션] 이 클래스에는 @Transactional이 없다. HTTP 호출을 트랜잭션 안에 넣지 않기
 * 위해서이며, DB 갱신은 PaymentTxService가 짧은 트랜잭션으로 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String DEFAULT_CANCEL_REASON = "고객 환불 요청";

    private final PaymentRepository paymentRepository;
    private final PaymentTxService paymentTxService;
    private final PassService passService;
    private final InicisClient inicisClient;
    private final InicisProperties props;
    private final ClinicRepository clinicRepository;
    private final StudentRepository studentRepository;

    // ───────────────────────────── 결제 시작 ─────────────────────────────

    /**
     * 결제 시작 — 주문번호를 발급하고 READY 행을 먼저 남긴다.
     *
     * 승인이 나기 전에 행을 만드는 이유는, 결제창까지 갔다가 이탈한 건도 남아야 나중에
     * 이니시스 정산 내역과 대조가 되기 때문이다. READY로 방치된 행은 배치가 정리한다.
     */
    public PaymentRespDTO.PrepareDTO prepare(PaymentReqDTO.PrepareDTO reqDTO) {
        PaymentRespDTO.ProductDTO product = paymentRepository.findActiveProduct(reqDTO.getProductCode());
        if (product == null) {
            throw new Exception404("판매 중인 상품이 아닙니다.");
        }

        String centerCode = clinicRepository.findCenterCode(reqDTO.getStudentId());
        String billingYm = passService.nextBillingYm(reqDTO.getStudentId(), product.getServiceCode());

        // 중복 결제 방지(2026-08-07) — 다른 기기에서 같은 학생·서비스·청구월로 이미 결제창을
        // 열어뒀거나(READY) 결제를 끝냈으면(PAID), 새로 만들지 않고 그 주문을 그대로 재사용한다.
        // A기기로 결제창을 열어두고 방치한 사이 B기기로 새로 결제하면 둘 다 정상 승인돼서 같은 달
        // 결제가 이중으로 잡히는 사고를 막기 위함이다 — nextBillingYm()은 이미 발급된(승인 완료)
        // 이용권만 보고 계산해서, 진행 중인 다른 결제 시도는 그 계산에 안 잡힌다.
        PaymentRespDTO.PaymentDTO existing = paymentRepository.findActiveByStudentServiceBilling(
                reqDTO.getStudentId(), product.getServiceCode(), billingYm);
        if (existing != null) {
            if ("PAID".equals(existing.getStatus())) {
                throw new Exception400("이미 이번 달 결제가 완료됐어요.");
            }
            return new PaymentRespDTO.PrepareDTO(existing.getOrderNo(), props.getMid(), existing.getAmount(),
                    existing.getProductName() + " (" + monthLabel(billingYm) + ")",
                    props.getReturnUrl(), props.getCloseUrl(), props.isTestMode());
        }

        String orderNo = newOrderNo();
        try {
            paymentRepository.insertReady(orderNo, null, reqDTO.getStudentId(), centerCode,
                    product.getProductId(), product.getProductName(), product.getServiceCode(), billingYm,
                    product.getPrice());
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // 위 체크와 이 INSERT 사이의 아주 좁은 순간에 다른 기기가 먼저 같은 조합으로 INSERT
            // 했을 때만 여기로 온다 — UX_payment_active_billing 유니크 인덱스가 막아준 것이다.
            log.warn("[결제] 중복 결제 시도 차단(DB 유니크 인덱스) — studentId={}, serviceCode={}, billingYm={}",
                    reqDTO.getStudentId(), product.getServiceCode(), billingYm, e);
            throw new Exception400("이미 이번 달 결제가 진행 중이거나 완료됐어요. 새로고침 후 다시 시도해주세요.");
        }

        return new PaymentRespDTO.PrepareDTO(orderNo, props.getMid(), product.getPrice(),
                product.getProductName() + " (" + monthLabel(billingYm) + ")",
                props.getReturnUrl(), props.getCloseUrl(), props.isTestMode());
    }

    /** 판매중인 상품 조회 — 형제 선택 화면에서 학생별 개별 금액을 보여줄 때 쓴다 */
    public PaymentRespDTO.ProductDTO productByCode(String productCode) {
        return paymentRepository.findActiveProduct(productCode);
    }

    /**
     * 형제 묶음결제 시작 — 선택된 학생마다 개별 READY 행을 만들고 공통 group_order_no로 묶는다.
     *
     * 모든 학생이 같은 상품을 산다는 전제다(형제마다 다른 상품을 고르는 것은 이번 범위 밖).
     * PG에는 이 그룹 주문번호 하나로 합계 금액을 결제 1건만 승인 요청한다 — 학생별 결제 내역/이용권은
     * 승인 확정 시점(approveGroupMobileByReturn)에 학생 수만큼 나뉘어 저장된다.
     */
    public PaymentRespDTO.PrepareGroupDTO prepareGroup(List<String> studentIds, String productCode) {
        if (studentIds == null || studentIds.isEmpty()) {
            throw new Exception400("결제할 학생을 선택해주세요.");
        }
        PaymentRespDTO.ProductDTO product = paymentRepository.findActiveProduct(productCode);
        if (product == null) {
            throw new Exception404("판매 중인 상품이 아닙니다.");
        }

        // 중복 결제 방지(2026-08-07) — 그룹 행을 만들기 전에 전원부터 검사한다. 검사와 INSERT를
        // 섞으면 일부 학생만 만들어진 채로 막혀서 그룹 주문이 절반만 생기는 상태가 될 수 있다.
        // 각자의 청구월도 여기서 같이 미리 구해 아래 INSERT 단계에서 재사용한다.
        Map<String, String> billingYmByStudent = new LinkedHashMap<>();
        for (String studentId : studentIds) {
            String billingYm = passService.nextBillingYm(studentId, product.getServiceCode());
            billingYmByStudent.put(studentId, billingYm);

            PaymentRespDTO.PaymentDTO existing = paymentRepository.findActiveByStudentServiceBilling(
                    studentId, product.getServiceCode(), billingYm);
            if (existing != null) {
                throw new Exception400(("PAID".equals(existing.getStatus())
                        ? "이미 이번 달 결제가 완료된 학생이 있어요: "
                        : "이미 결제가 진행 중인 학생이 있어요: ") + studentId);
            }
        }

        String groupOrderNo = newOrderNo();
        int totalAmount = 0;
        // 형제라도 이미 커버된 달이 서로 다를 수 있다(한 명이 먼저 다음 달 걸 사둔 경우 등) —
        // 그룹 전체에 한 달을 통일해서 정하지 않고 학생마다 각자의 nextBillingYm을 따로 구한다.
        Set<String> billingYms = new LinkedHashSet<>();
        try {
            for (String studentId : studentIds) {
                String centerCode = clinicRepository.findCenterCode(studentId);
                String orderNo = newOrderNo();
                String billingYm = billingYmByStudent.get(studentId);
                billingYms.add(billingYm);
                paymentRepository.insertReady(orderNo, groupOrderNo, studentId, centerCode,
                        product.getProductId(), product.getProductName(), product.getServiceCode(), billingYm,
                        product.getPrice());
                totalAmount += product.getPrice();
            }
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // 위 사전 검사와 INSERT 사이의 아주 좁은 순간에 다른 기기가 먼저 같은 조합으로
            // INSERT했을 때만 여기로 온다 — UX_payment_active_billing 유니크 인덱스가 막아준 것.
            // 이미 만든 그룹원 행이 일부 있을 수 있지만 group_order_no가 새로 발급된 값이라
            // 다른 정상 그룹과 섞이지 않고, 전부 READY로 방치되면 PaymentCleanupJob이 정리한다.
            log.warn("[결제] 중복 결제 시도 차단(DB 유니크 인덱스, 그룹) — productCode={}", productCode, e);
            throw new Exception400("이미 이번 달 결제가 진행 중이거나 완료된 학생이 있어요. 새로고침 후 다시 시도해주세요.");
        }

        // 그룹 전원의 대상월이 같으면 화면에 그 달을 보여주고, 갈리면(드문 경우) 잘못된 달을
        // 하나로 뭉뚱그려 보여주는 것보다는 월 표시 자체를 생략하는 편이 안전하다.
        String monthSuffix = billingYms.size() == 1 ? " (" + monthLabel(billingYms.iterator().next()) + ")" : "";
        String goodName = studentIds.size() > 1
                ? product.getProductName() + monthSuffix + " 외 " + (studentIds.size() - 1) + "명"
                : product.getProductName() + monthSuffix;

        return new PaymentRespDTO.PrepareGroupDTO(groupOrderNo, props.getMid(), totalAmount, goodName,
                props.getReturnUrl(), props.getCloseUrl(), props.isTestMode());
    }

    // ───────────────────────────── 승인 ─────────────────────────────

    /**
     * 승인 — 결제창 인증까지 끝난 건을 실제 결제로 확정한다.
     *
     * authUrl/netCancelUrl은 앱이 보내온 값이라 그대로 믿으면 안 된다. 공격자가 자기 서버
     * 주소를 넣으면 "resultCode=0000" 위조 응답으로 이용권을 받아갈 수 있으므로,
     * 이니시스 도메인인지 먼저 검증한다.
     */
    public PaymentRespDTO.ApproveDTO approve(PaymentReqDTO.ApproveDTO reqDTO) {
        assertInicisUrl(reqDTO.getAuthUrl());

        PaymentRespDTO.PaymentDTO payment = paymentRepository.findByOrderNo(reqDTO.getOrderNo());
        if (payment == null) {
            throw new Exception404("결제 정보를 찾을 수 없습니다.");
        }
        if (!payment.getStudentId().equals(reqDTO.getStudentId())) {
            throw new Exception400("다른 학생의 결제입니다.");
        }
        if ("PAID".equals(payment.getStatus())) {
            // 앱의 재시도. 이미 확정된 건이므로 다시 승인하지 않고 현재 상태를 그대로 돌려준다.
            return approveResult(payment, passService.remain(payment.getStudentId(), serviceCodeOf(payment)));
        }
        if (!"READY".equals(payment.getStatus())) {
            throw new Exception400("이미 종료된 결제입니다.");
        }

        InicisClient.Result result;
        try {
            result = inicisClient.approve(reqDTO.getAuthUrl(), reqDTO.getAuthToken());
        } catch (Exception e) {
            // 응답을 못 받았다고 승인이 안 난 것은 아니다(타임아웃이면 카드사에는 남아 있을 수 있다).
            // 그래서 실패로 끝내지 않고 망취소부터 때린다.
            log.error("[결제] 승인 호출 실패 — orderNo={}", reqDTO.getOrderNo(), e);
            paymentRepository.insertLog(reqDTO.getOrderNo(), null, "APPROVE", null, null, null, e.toString());
            netCancel(reqDTO, "승인 호출 실패");
            paymentTxService.confirmFailed(reqDTO.getOrderNo(), null);
            throw new Exception500("결제 승인에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }

        paymentRepository.insertLog(reqDTO.getOrderNo(), result.get("tid"), "APPROVE",
                result.httpStatus(), result.get("resultCode"), null, mask(result.rawBody()));

        if (!result.isApproveSuccess()) {
            paymentTxService.confirmFailed(reqDTO.getOrderNo(), result.get("resultCode"));
            throw new Exception400(nvl(result.get("resultMsg"), "결제가 승인되지 않았습니다."));
        }

        // 위변조 검증 — 승인된 금액이 우리가 기록한 금액과 다르면 결제로 인정하지 않는다.
        int approvedAmount = parseAmount(result.get("TotPrice"));
        if (approvedAmount != payment.getAmount()) {
            log.error("[결제] 금액 불일치 — orderNo={}, 기록={}, 승인={}",
                    payment.getOrderNo(), payment.getAmount(), approvedAmount);
            netCancel(reqDTO, "금액 불일치");
            paymentTxService.confirmFailed(reqDTO.getOrderNo(), result.get("resultCode"));
            throw new Exception400("결제 금액이 일치하지 않아 취소되었습니다.");
        }

        // 판매중 여부는 보지 않는다. 결제가 진행되는 사이 본사가 상품을 내렸다고 해서 이미 승인된
        // 결제를 되돌리면 고객은 아무 잘못 없이 결제가 취소된다.
        PaymentRespDTO.ProductDTO product = paymentRepository.findProductById(payment.getProductId());
        if (product == null) {
            // FK가 있어 정상적으로는 생길 수 없다. 그래도 이용권을 못 주는 상태라 승인을 되돌린다.
            netCancel(reqDTO, "상품 정보 없음");
            paymentTxService.confirmFailed(reqDTO.getOrderNo(), result.get("resultCode"));
            throw new Exception500("상품 정보를 찾을 수 없어 결제가 취소되었습니다.");
        }

        boolean updated;
        try {
            updated = paymentTxService.confirmPaid(payment, product,
                    result.get("tid"), nvl(result.get("payMethod"), "Card"),
                    result.get("CARD_Name"), maskCardNo(result.get("CARD_Num")),
                    result.get("applNum"), result.get("resultCode"));
        } catch (Exception e) {
            // DB 확정에 실패했는데 승인은 나 있는 상태 — 가장 위험한 지점이라 반드시 되돌린다.
            log.error("[결제] 승인 확정 실패 — 망취소 진행. orderNo={}", reqDTO.getOrderNo(), e);
            netCancel(reqDTO, "확정 실패");
            throw new Exception500("결제 처리에 실패했습니다. 결제는 취소되었습니다.");
        }

        if (!updated) {
            // markPaid가 0행이면 이 order_no는 이미 누군가 PAID로 확정해놨다는 뜻인데, 그게
            // "내가 방금 보낸 것과 같은 tid의 재시도"인지 "전혀 다른 tid로 거의 동시에 이중 승인된
            // 것"인지 구분해야 한다(2026-08-07). 후자면 방금 우리가 이니시스에서 받아온 승인
            // (result.get("tid"))은 우리 DB 어디에도 안 남는 채로 카드사에만 남는 orphan 승인이라,
            // 그대로 두면 고객 카드에서 조용히 중복으로 돈이 빠진다 — 즉시 망취소한다.
            PaymentRespDTO.PaymentDTO current = paymentRepository.findByOrderNo(reqDTO.getOrderNo());
            String justApprovedTid = result.get("tid");
            if (current != null && current.getTid() != null && !current.getTid().equals(justApprovedTid)) {
                log.error("[결제] 동시 승인 경합 — 같은 주문에 서로 다른 tid로 이중 승인됨. 방금 승인분 망취소. "
                                + "orderNo={}, 기존tid={}, 방금tid={}",
                        reqDTO.getOrderNo(), current.getTid(), justApprovedTid);
                netCancel(reqDTO, "동시 승인 경합 — 중복 승인분 취소");
            } else {
                log.info("[결제] 승인 중복 요청(재시도) — orderNo={}", reqDTO.getOrderNo());
            }
        }

        int remain = passService.remain(payment.getStudentId(), product.getServiceCode());
        return approveResult(paymentRepository.findByOrderNo(reqDTO.getOrderNo()), remain);
    }

    /**
     * 결제창 returnUrl로 돌아온 인증 결과를 승인한다 — 세션에 기대지 않는 승인 경로.
     *
     * 결제창에서 우리 서버로 돌아오는 POST는 이니시스 도메인에서 출발하는 cross-site 요청이라
     * SameSite=Lax인 세션 쿠키가 딸려오지 않는다. 즉 이 시점에는 "로그인한 학생"을 알 수 없다.
     * 대신 주문번호로 결제 행을 찾아 그때 기록해 둔 studentId를 쓴다 — 본인 확인은 결제를
     * 시작할 때(prepare) 이미 끝났고, 여기서 신원을 증명하는 것은 이니시스가 발급한 authToken이다.
     */
    public PaymentRespDTO.ApproveDTO approveByReturn(String orderNo, String authToken,
                                                     String authUrl, String netCancelUrl) {
        PaymentRespDTO.PaymentDTO payment = paymentRepository.findByOrderNo(orderNo);
        if (payment == null) {
            throw new Exception404("결제 정보를 찾을 수 없습니다.");
        }

        PaymentReqDTO.ApproveDTO reqDTO = new PaymentReqDTO.ApproveDTO();
        reqDTO.setStudentId(payment.getStudentId());
        reqDTO.setOrderNo(orderNo);
        reqDTO.setAuthToken(authToken);
        reqDTO.setAuthUrl(authUrl);
        reqDTO.setNetCancelUrl(netCancelUrl);

        return approve(reqDTO);
    }

    /**
     * 모바일 결제창 승인 — 인증 응답의 P_REQ_URL을 호출하는 것이 곧 승인이다.
     *
     * [PC와 다른 점] PC는 authToken을 authUrl로 보내 승인하고, 실패하면 netCancelUrl로
     * 되돌린다. 모바일은 P_REQ_URL 호출 자체가 승인이라 "승인은 났는데 확정 못 한" 상태를
     * 되돌릴 망취소 주소가 따로 없다. 그런 상황에서는 취소 API로 취소해야 하는데 그건
     * apiKey가 필요하므로, 지금은 로그에 남겨 사람이 상점관리자에서 취소하도록 한다.
     *
     * [검증 순서] 금액 검증은 승인 전에 한 번(P_AMT), 승인 후 한 번(응답 금액) 한다.
     * 인증 단계 금액이 이미 우리 기록과 다르면 승인 요청 자체를 보내지 않는 편이 안전하다.
     */
    public PaymentRespDTO.ApproveDTO approveMobileByReturn(String orderNo, String reqUrl,
                                                           String tid, String authAmount) {
        assertInicisUrl(reqUrl);

        PaymentRespDTO.PaymentDTO payment = paymentRepository.findByOrderNo(orderNo);
        if (payment == null) {
            // 단일결제 order_no로 못 찾았으면 형제 묶음결제의 그룹 주문번호일 수 있다 — 결제창에서
            // 그룹 결제일 때는 P_OID/P_NOTI에 group_order_no를 실어 보내기 때문이다.
            List<PaymentRespDTO.PaymentDTO> group = paymentRepository.findByGroupOrderNo(orderNo);
            if (!group.isEmpty()) {
                return approveGroupMobileByReturn(orderNo, group, reqUrl, tid, authAmount);
            }
            throw new Exception404("결제 정보를 찾을 수 없습니다.");
        }
        if ("PAID".equals(payment.getStatus())) {
            return approveResult(payment, passService.remain(payment.getStudentId(), serviceCodeOf(payment)));
        }
        if (!"READY".equals(payment.getStatus())) {
            throw new Exception400("이미 종료된 결제입니다.");
        }
        if (parseAmount(authAmount) != payment.getAmount()) {
            log.error("[결제] 인증 금액 불일치 — orderNo={}, 기록={}, 인증={}",
                    orderNo, payment.getAmount(), authAmount);
            paymentTxService.confirmFailed(orderNo, null);
            throw new Exception400("결제 금액이 일치하지 않습니다.");
        }

        InicisClient.Result result;
        try {
            result = inicisClient.approveMobile(reqUrl, tid);
        } catch (Exception e) {
            log.error("[결제] 모바일 승인 호출 실패 — orderNo={}", orderNo, e);
            paymentRepository.insertLog(orderNo, tid, "APPROVE", null, null, null, e.toString());
            paymentTxService.confirmFailed(orderNo, null);
            throw new Exception500("결제 승인에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }

        paymentRepository.insertLog(orderNo, result.get("P_TID"), "APPROVE",
                result.httpStatus(), result.get("P_STATUS"), null, mask(result.rawBody()));

        if (!result.isMobileSuccess()) {
            paymentTxService.confirmFailed(orderNo, result.get("P_STATUS"));
            throw new Exception400(nvl(result.get("P_RMESG1"), "결제가 승인되지 않았습니다."));
        }

        int approvedAmount = parseAmount(result.get("P_AMT"));
        if (approvedAmount != payment.getAmount()) {
            // 승인은 이미 났다. 되돌릴 수단(망취소)이 모바일에는 없어 수동 취소 대상으로 남긴다.
            log.error("[결제] 승인 금액 불일치 — 수동 취소 필요. orderNo={}, tid={}, 기록={}, 승인={}",
                    orderNo, result.get("P_TID"), payment.getAmount(), approvedAmount);
            paymentRepository.insertLog(orderNo, result.get("P_TID"), "NET_CANCEL", null, null,
                    "승인 금액 불일치 — 수동 취소 필요", null);
            paymentTxService.confirmFailed(orderNo, result.get("P_STATUS"));
            paymentRepository.markNeedsReview(orderNo, "승인 금액 불일치(모바일) — 상점관리자에서 직접 취소 필요");
            throw new Exception400("결제 금액이 일치하지 않습니다. 고객센터로 문의해주세요.");
        }

        PaymentRespDTO.ProductDTO product = paymentRepository.findProductById(payment.getProductId());
        if (product == null) {
            log.error("[결제] 상품 정보 없음 — 수동 취소 필요. orderNo={}, tid={}", orderNo, result.get("P_TID"));
            paymentRepository.markNeedsReview(orderNo, "승인은 났으나 상품 정보 없음 — 상점관리자에서 직접 취소 필요");
            throw new Exception500("상품 정보를 찾을 수 없습니다. 고객센터로 문의해주세요.");
        }

        boolean updated;
        try {
            updated = paymentTxService.confirmPaid(payment, product, result.get("P_TID"), "Card",
                    result.get("P_FN_NM"), maskCardNo(result.get("P_CARD_NUM")),
                    result.get("P_AUTH_NO"), result.get("P_STATUS"));
        } catch (Exception e) {
            log.error("[결제] 승인 확정 실패 — 수동 취소 필요. orderNo={}, tid={}", orderNo, result.get("P_TID"), e);
            paymentRepository.markNeedsReview(orderNo, "승인은 났으나 확정 실패 — 상점관리자에서 직접 취소 필요");
            throw new Exception500("결제 처리에 실패했습니다. 고객센터로 문의해주세요.");
        }

        if (!updated) {
            // 모바일은 망취소 수단이 없어(클래스 상단 주석 참고) PC처럼 자동으로 되돌릴 수 없다 —
            // 다른 tid로 이중 승인된 것으로 보이면 사람이 상점관리자에서 직접 확인해야 한다(2026-08-07).
            PaymentRespDTO.PaymentDTO current = paymentRepository.findByOrderNo(orderNo);
            String justApprovedTid = result.get("P_TID");
            if (current != null && current.getTid() != null && !current.getTid().equals(justApprovedTid)) {
                log.error("[결제] 동시 승인 경합(모바일) — 같은 주문에 서로 다른 tid로 이중 승인됨. 수동 취소 필요. "
                                + "orderNo={}, 기존tid={}, 방금tid={}",
                        orderNo, current.getTid(), justApprovedTid);
                paymentRepository.markNeedsReview(orderNo,
                        "동시 승인 경합(모바일) — 방금 승인된 tid가 orphan일 수 있음, 상점관리자에서 확인 필요");
            } else {
                log.info("[결제] 승인 중복 요청(재시도, 모바일) — orderNo={}", orderNo);
            }
        }

        int remain = passService.remain(payment.getStudentId(), product.getServiceCode());
        return approveResult(paymentRepository.findByOrderNo(orderNo), remain);
    }

    /**
     * 형제 묶음결제 승인 — approveMobileByReturn과 같은 단계를 그대로 따라가되 대상이
     * 학생 여러 명(리스트)이라는 것만 다르다. PG 승인은 합계 금액으로 한 번만 호출하고,
     * 승인 확정은 PaymentTxService.confirmPaidGroup이 학생 수만큼 한 트랜잭션 안에서 반복한다
     * (일부 학생만 확정되는 상황을 막기 위해 전원 확정이 하나의 트랜잭션이어야 한다).
     *
     * 모든 학생이 같은 상품을 샀다는 전제라 product/serviceCode는 그룹의 첫 행 기준으로 하나만 조회한다.
     */
    private PaymentRespDTO.ApproveDTO approveGroupMobileByReturn(String groupOrderNo,
            List<PaymentRespDTO.PaymentDTO> group, String reqUrl, String tid, String authAmount) {
        boolean allPaid = group.stream().allMatch(p -> "PAID".equals(p.getStatus()));
        if (allPaid) {
            PaymentRespDTO.PaymentDTO first = group.get(0);
            return approveResult(first, passService.remain(first.getStudentId(), serviceCodeOf(first)));
        }
        boolean allReady = group.stream().allMatch(p -> "READY".equals(p.getStatus()));
        if (!allReady) {
            throw new Exception400("이미 종료된 결제입니다.");
        }

        int recordedAmount = group.stream().mapToInt(PaymentRespDTO.PaymentDTO::getAmount).sum();
        if (parseAmount(authAmount) != recordedAmount) {
            log.error("[결제] 인증 금액 불일치(그룹) — groupOrderNo={}, 기록={}, 인증={}",
                    groupOrderNo, recordedAmount, authAmount);
            paymentTxService.confirmFailedGroup(orderNosOf(group), null);
            throw new Exception400("결제 금액이 일치하지 않습니다.");
        }

        InicisClient.Result result;
        try {
            result = inicisClient.approveMobile(reqUrl, tid);
        } catch (Exception e) {
            log.error("[결제] 모바일 승인 호출 실패(그룹) — groupOrderNo={}", groupOrderNo, e);
            paymentRepository.insertLog(groupOrderNo, tid, "APPROVE", null, null, null, e.toString());
            paymentTxService.confirmFailedGroup(orderNosOf(group), null);
            throw new Exception500("결제 승인에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }

        paymentRepository.insertLog(groupOrderNo, result.get("P_TID"), "APPROVE",
                result.httpStatus(), result.get("P_STATUS"), null, mask(result.rawBody()));

        if (!result.isMobileSuccess()) {
            paymentTxService.confirmFailedGroup(orderNosOf(group), result.get("P_STATUS"));
            throw new Exception400(nvl(result.get("P_RMESG1"), "결제가 승인되지 않았습니다."));
        }

        int approvedAmount = parseAmount(result.get("P_AMT"));
        if (approvedAmount != recordedAmount) {
            log.error("[결제] 승인 금액 불일치(그룹) — 수동 취소 필요. groupOrderNo={}, tid={}, 기록={}, 승인={}",
                    groupOrderNo, result.get("P_TID"), recordedAmount, approvedAmount);
            paymentRepository.insertLog(groupOrderNo, result.get("P_TID"), "NET_CANCEL", null, null,
                    "승인 금액 불일치 — 수동 취소 필요", null);
            paymentTxService.confirmFailedGroup(orderNosOf(group), result.get("P_STATUS"));
            markNeedsReviewGroup(group, "승인 금액 불일치(그룹) — 상점관리자에서 직접 취소 필요");
            throw new Exception400("결제 금액이 일치하지 않습니다. 고객센터로 문의해주세요.");
        }

        PaymentRespDTO.ProductDTO product = paymentRepository.findProductById(group.get(0).getProductId());
        if (product == null) {
            log.error("[결제] 상품 정보 없음(그룹) — 수동 취소 필요. groupOrderNo={}, tid={}", groupOrderNo, result.get("P_TID"));
            markNeedsReviewGroup(group, "승인은 났으나 상품 정보 없음(그룹) — 상점관리자에서 직접 취소 필요");
            throw new Exception500("상품 정보를 찾을 수 없습니다. 고객센터로 문의해주세요.");
        }

        boolean allUpdated;
        try {
            allUpdated = paymentTxService.confirmPaidGroup(group, product, result.get("P_TID"), "Card",
                    result.get("P_FN_NM"), maskCardNo(result.get("P_CARD_NUM")),
                    result.get("P_AUTH_NO"), result.get("P_STATUS"));
        } catch (Exception e) {
            log.error("[결제] 승인 확정 실패(그룹) — 수동 취소 필요. groupOrderNo={}, tid={}", groupOrderNo, result.get("P_TID"), e);
            markNeedsReviewGroup(group, "승인은 났으나 확정 실패(그룹) — 상점관리자에서 직접 취소 필요");
            throw new Exception500("결제 처리에 실패했습니다. 고객센터로 문의해주세요.");
        }

        if (!allUpdated) {
            // 그룹원 중 한 명이라도 이미 PAID라 갱신할 게 없었다는 뜻 — 동시 승인 경합으로 방금
            // 이니시스에서 또 승인을 받아버렸을 수 있다. 모바일은 망취소 수단이 없고, 그룹은
            // 멤버별 tid 비교까지 하기엔 복잡해서(단일결제처럼 정밀하게 구분하지 않고) 일단 전원을
            // 확인 대상으로 남긴다(2026-08-07).
            log.error("[결제] 동시 승인 경합 가능성(그룹) — 일부 이미 처리됨, 수동 확인 필요. groupOrderNo={}, tid={}",
                    groupOrderNo, result.get("P_TID"));
            markNeedsReviewGroup(group,
                    "동시 승인 경합 가능성(그룹) — 방금 승인된 tid가 orphan일 수 있음, 상점관리자에서 확인 필요");
        }

        PaymentRespDTO.PaymentDTO first = paymentRepository.findByOrderNo(group.get(0).getOrderNo());
        int remain = passService.remain(first.getStudentId(), product.getServiceCode());
        return approveResult(first, remain);
    }

    private List<String> orderNosOf(List<PaymentRespDTO.PaymentDTO> payments) {
        return payments.stream().map(PaymentRespDTO.PaymentDTO::getOrderNo).toList();
    }

    /** 그룹 전원 각자의 order_no에 확인 필요 표시를 남긴다 — 목록 화면은 order_no 단위로 보여준다 */
    private void markNeedsReviewGroup(List<PaymentRespDTO.PaymentDTO> group, String reason) {
        for (String orderNo : orderNosOf(group)) {
            paymentRepository.markNeedsReview(orderNo, reason);
        }
    }

    /**
     * 망취소 — 실패해도 예외를 위로 올리지 않는다. 이 시점의 호출부는 이미 실패 경로를 타고
     * 있어서, 여기서 또 예외가 나면 원래 실패 원인이 가려진다. 대신 로그에 남겨 사람이
     * 상점관리자에서 수동 취소하도록 한다.
     */
    private void netCancel(PaymentReqDTO.ApproveDTO reqDTO, String why) {
        if (reqDTO.getNetCancelUrl() == null || reqDTO.getNetCancelUrl().isBlank()) {
            log.error("[결제] 망취소 주소 없음 — 수동 취소 필요. orderNo={}, 사유={}", reqDTO.getOrderNo(), why);
            paymentRepository.markNeedsReview(reqDTO.getOrderNo(), "망취소 주소 없음 — " + why);
            return;
        }
        try {
            assertInicisUrl(reqDTO.getNetCancelUrl());
            InicisClient.Result result = inicisClient.netCancel(reqDTO.getNetCancelUrl(), reqDTO.getAuthToken());
            paymentRepository.insertLog(reqDTO.getOrderNo(), result.get("tid"), "NET_CANCEL",
                    result.httpStatus(), result.get("resultCode"), why, mask(result.rawBody()));
            if (!result.isApproveSuccess()) {
                log.error("[결제] 망취소 실패 — 수동 취소 필요. orderNo={}, resultCode={}",
                        reqDTO.getOrderNo(), result.get("resultCode"));
                paymentRepository.markNeedsReview(reqDTO.getOrderNo(), "망취소 실패(" + why + ") — 상점관리자에서 직접 취소 필요");
            }
        } catch (Exception e) {
            log.error("[결제] 망취소 호출 실패 — 수동 취소 필요. orderNo={}", reqDTO.getOrderNo(), e);
            paymentRepository.insertLog(reqDTO.getOrderNo(), null, "NET_CANCEL", null, null, why, e.toString());
            paymentRepository.markNeedsReview(reqDTO.getOrderNo(), "망취소 호출 실패(" + why + ") — 상점관리자에서 직접 취소 필요");
        }
    }

    // ───────────────────────────── 환불 ─────────────────────────────

    /**
     * 환불 견적 — 규정을 적용하면 얼마가 나오는지 미리 계산한다.
     * 실제 환불과 같은 계산기를 쓴다. 화면에 보여준 금액과 실제 환불액이 다르면 그 자체가 분쟁이다.
     */
    public PaymentRespDTO.RefundQuoteDTO quote(String studentId, int paymentId) {
        PaymentRespDTO.PaymentDTO payment = requireOwnPayment(studentId, paymentId);
        return calculateRefund(payment);
    }

    /**
     * 환불 실행 — 앱에서 신청하면 규정이 자동 적용되고 PG 취소까지 간다.
     *
     * 요청 기록을 PG 호출 "전"에 남긴다. 호출 도중 서버가 죽으면 신청 흔적조차 없어져
     * "환불 신청했는데 아무 일도 안 일어났다"가 되기 때문이다.
     */
    public PaymentRespDTO.RefundQuoteDTO refund(String studentId, int paymentId, String reason,
                                                String requestedBy) {
        PaymentRespDTO.PaymentDTO payment = requireOwnPayment(studentId, paymentId);

        // 동시 환불 요청 경합 방지 — "환불 안 됨"을 확인만 하고 실제 PG 호출까지 가는 사이에 락이
        // 없으면, 거의 동시에 두 번 요청됐을 때 둘 다 통과해 PG 취소가 이중으로 나갈 수 있다
        // (2026-08-07). markPaid의 WHERE status='READY'와 같은 원리로 원자적 UPDATE 한 번으로
        // 선점하고, 뒤 요청은 여기서 막는다.
        if (paymentRepository.claimRefund(paymentId) == 0) {
            throw new Exception400("이미 환불이 진행 중이거나 완료된 결제입니다. 잠시 후 다시 확인해주세요.");
        }
        try {
            return doRefund(payment, paymentId, reason, requestedBy);
        } finally {
            // 성공/실패 무관하게 선점을 풀어준다 — 성공은 refund_amount>0이 영구 차단하고
            // (calculateRefund), 실패는 재시도가 가능해야 하므로 여기서 다시 열어준다.
            paymentRepository.releaseRefundClaim(paymentId);
        }
    }

    private PaymentRespDTO.RefundQuoteDTO doRefund(PaymentRespDTO.PaymentDTO payment, int paymentId, String reason,
                                                    String requestedBy) {
        PaymentRespDTO.RefundQuoteDTO quote = calculateRefund(payment);
        if (!quote.isRefundable()) {
            throw new Exception400(quote.getReason());
        }

        PassRespDTO.PassDTO pass = passService.findByRef(PassService.SOURCE_PG, payment.getOrderNo());
        String cancelReason = nvl(reason, DEFAULT_CANCEL_REASON);

        int cancelId = paymentTxService.openCancel(paymentId, quote.getRefundAmount(), cancelReason,
                requestedBy, quote.getRuleCode(), quote.getUsedDays(), quote.getUsedCount());

        // tid 1건에 걸린 실제 PG 승인 금액은 이 결제 행 하나가 아니라(형제 묶음결제라면) 그룹
        // 전체의 합계다. 여기서 "이 행만" 기준으로 remainAfter를 0으로 잘못 계산하면, 전액취소로
        // 오인해 형제 전원의 결제가 걸린 tid 전체를 취소해버린다 — 다른 형제 몫까지 카드사에서
        // 사라지는데 우리 DB에는 그 행이 여전히 PAID로 남는 정합성 사고로 이어진다.
        // 그룹이 아니면(groupOrderNo=null) 아래 계산은 이 행 하나로 좁혀져 기존 단건 계산과 같다.
        int groupTotalAmount = payment.getAmount();
        int groupTotalRefunded = payment.getRefundAmount();
        if (payment.getGroupOrderNo() != null) {
            List<PaymentRespDTO.PaymentDTO> group = paymentRepository.findByGroupOrderNo(payment.getGroupOrderNo());
            groupTotalAmount = group.stream().mapToInt(PaymentRespDTO.PaymentDTO::getAmount).sum();
            groupTotalRefunded = group.stream().mapToInt(PaymentRespDTO.PaymentDTO::getRefundAmount).sum();
        }

        int remainAfter = groupTotalAmount - groupTotalRefunded - quote.getRefundAmount();
        boolean partial = remainAfter > 0;

        InicisClient.Result result;
        try {
            result = inicisClient.refund(payment.getTid(), cancelReason,
                    partial ? quote.getRefundAmount() : null, remainAfter);
        } catch (Exception e) {
            log.error("[환불] 호출 실패 — paymentId={}", paymentId, e);
            paymentRepository.insertLog(payment.getOrderNo(), payment.getTid(), "CANCEL",
                    null, null, cancelReason, e.toString());
            paymentTxService.confirmRefundFailed(cancelId, null, "환불 요청 중 오류가 발생했습니다.");
            throw new Exception500("환불 처리에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }

        paymentRepository.insertLog(payment.getOrderNo(), payment.getTid(), "CANCEL",
                result.httpStatus(), result.get("resultCode"), cancelReason, mask(result.rawBody()));

        if (!result.isRefundSuccess()) {
            paymentTxService.confirmRefundFailed(cancelId, result.get("resultCode"),
                    nvl(result.get("resultMsg"), "환불이 거절되었습니다."));
            throw new Exception400(nvl(result.get("resultMsg"), "환불이 거절되었습니다."));
        }

        paymentTxService.confirmRefunded(paymentId, cancelId, quote.getRefundAmount(),
                pass == null ? null : pass.getPassId(),
                result.get("tid"), result.get("resultCode"), result.get("resultMsg"));

        return quote;
    }

    /**
     * 환불 규정 적용 — priority 순으로 훑어 조건에 처음 맞는 규정 하나만 쓴다.
     * 어디에도 안 걸리면 환불 불가다(규정에 없는 환불을 코드가 임의로 만들어내지 않는다).
     *
     * 사용 횟수만으로 판정한다(2026-08-05, 날짜 조건 폐지) — 0회 전액, 1회 75%, 2회 50%,
     * 3회 이상은 규정에 맞는 행이 없어 자연히 환불 불가로 떨어진다. usedDays는 더 이상 판정에
     * 쓰지 않지만 payment_cancel 감사 스냅샷에 여전히 남기므로 계산은 유지한다.
     *
     * 환불은 결제 1건당 1번만 허용한다(2026-08-05) — refund_amount가 조금이라도 있으면 그걸로
     * 끝이다. 부분환불(예: 75%) 후 남은 잔액을 다시 계산해서 또 내주면, 두 번 요청하는 것만으로
     * 사실상 100% 환불이 되어 "1회 사용 시 75%까지만" 같은 규정이 무의미해진다.
     */
    private PaymentRespDTO.RefundQuoteDTO calculateRefund(PaymentRespDTO.PaymentDTO payment) {
        if (!"PAID".equals(payment.getStatus())) {
            return refundDenied("환불할 수 있는 결제가 아닙니다.");
        }
        if (payment.getRefundAmount() > 0) {
            return refundDenied("이미 환불 처리된 결제입니다.");
        }
        if (payment.getPaidAt() == null || payment.getTid() == null) {
            return refundDenied("승인 정보가 없어 환불할 수 없습니다.");
        }

        int usedDays = (int) ChronoUnit.DAYS.between(payment.getPaidAt().toLocalDate(), KstClock.today());

        PassRespDTO.PassDTO pass = passService.findByRef(PassService.SOURCE_PG, payment.getOrderNo());
        int usedCount = pass == null ? 0 : passService.usedCount(pass.getPassId());

        List<PaymentRespDTO.RefundRuleDTO> rules = paymentRepository.findActiveRefundRules();
        for (PaymentRespDTO.RefundRuleDTO rule : rules) {
            if (usedCount > rule.getMaxCount()) {
                continue;
            }
            // 원 단위 절사 — 정수 나눗셈이 그대로 절사다. 카드사에 원 미만 금액은 넘길 수 없다.
            int refundAmount = payment.getAmount() * rule.getRefundRate() / 100;
            // 이미 일부 환불된 건이면 남은 한도까지만
            int refundable = payment.getAmount() - payment.getRefundAmount();
            refundAmount = Math.min(refundAmount, refundable);

            if (refundAmount <= 0) {
                return refundDenied("환불 가능 금액이 없습니다.");
            }
            return new PaymentRespDTO.RefundQuoteDTO(true, null, rule.getRuleCode(), rule.getRuleName(),
                    usedDays, usedCount, refundAmount);
        }

        return new PaymentRespDTO.RefundQuoteDTO(false, "환불 가능 기간이 지났거나 사용 횟수가 초과되었습니다.",
                null, null, usedDays, usedCount, 0);
    }

    private PaymentRespDTO.RefundQuoteDTO refundDenied(String reason) {
        return new PaymentRespDTO.RefundQuoteDTO(false, reason, null, null, 0, 0, 0);
    }

    // ───────────────────────────── 조회 ─────────────────────────────

    /**
     * 결제창을 다시 열 때 필요한 정보 조회 — orderNo가 단일결제 주문번호든 형제 묶음결제의
     * group_order_no든 구분 없이 받아 처리한다. 앱(Flutter)은 prepare/prepareGroup 응답에서
     * 받은 값을 그대로 orderNo로 넘기므로, 어느 쪽인지는 서버가 대신 판별해준다.
     */
    public PaymentRespDTO.PrepareGroupDTO findOwnReadyForCheckout(String studentId, String orderNo) {
        PaymentRespDTO.PaymentDTO single = paymentRepository.findByOrderNo(orderNo);
        if (single != null) {
            if (!single.getStudentId().equals(studentId)) {
                throw new Exception400("다른 학생의 결제입니다.");
            }
            if (!"READY".equals(single.getStatus())) {
                throw new Exception400("이미 종료된 결제입니다.");
            }
            return new PaymentRespDTO.PrepareGroupDTO(single.getOrderNo(), props.getMid(), single.getAmount(),
                    single.getProductName(), props.getReturnUrl(), props.getCloseUrl(), props.isTestMode());
        }

        List<PaymentRespDTO.PaymentDTO> group = paymentRepository.findByGroupOrderNo(orderNo);
        if (group.isEmpty()) {
            throw new Exception404("결제 정보를 찾을 수 없습니다.");
        }
        boolean owns = group.stream().anyMatch(p -> p.getStudentId().equals(studentId));
        if (!owns) {
            throw new Exception400("다른 학생의 결제입니다.");
        }
        boolean allReady = group.stream().allMatch(p -> "READY".equals(p.getStatus()));
        if (!allReady) {
            throw new Exception400("이미 종료된 결제입니다.");
        }

        int totalAmount = group.stream().mapToInt(PaymentRespDTO.PaymentDTO::getAmount).sum();
        String goodName = group.size() > 1
                ? group.get(0).getProductName() + " 외 " + (group.size() - 1) + "명"
                : group.get(0).getProductName();
        return new PaymentRespDTO.PrepareGroupDTO(orderNo, props.getMid(), totalAmount, goodName,
                props.getReturnUrl(), props.getCloseUrl(), props.isTestMode());
    }

    /** product_id로 상품 조회 — 결제창 화면이 상품명을 다시 그릴 때 쓴다 */
    public PaymentRespDTO.ProductDTO productByPaymentId(int productId) {
        return paymentRepository.findProductById(productId);
    }

    /**
     * 결제창 이탈 — 승인까지 못 간 READY 주문을 FAILED로 내린다.
     *
     * 별도 상태값(ABANDONED 등)을 만들지 않는다. "결제되지 않았다"는 사실은 같고,
     * 왜 안 됐는지는 payment_log를 보면 되기 때문이다. 상태값이 늘수록 이 값을 읽는
     * 화면과 쿼리가 전부 늘어난 값을 알아야 한다.
     * READY가 아닌 건(이미 승인된 건)은 markFailed의 WHERE 조건이 걸러낸다.
     */
    public void abandon(String orderNo) {
        paymentTxService.confirmClosed(orderNo);
        log.info("[결제] 결제창 이탈 정리 — orderNo={}", orderNo);
    }

    /**
     * 본인 확인 후 결제창 이탈 처리 — 앱의 X 버튼이 호출하는 경로.
     * abandon(orderNo)와 달리 본인 것이 아니면 거부한다. 이 경로는 세션이 있는 클라이언트가
     * 직접 호출하므로 남의 주문을 함부로 지우지 못하게 막아야 한다.
     */
    public void abandon(String studentId, String orderNo) {
        PaymentRespDTO.PaymentDTO payment = paymentRepository.findByOrderNo(orderNo);
        if (payment != null) {
            if (!payment.getStudentId().equals(studentId)) {
                return;   // 남의 주문이면 조용히 무시한다 — 앱 종료 타이밍 경합으로 흔히 생긴다
            }
            abandon(orderNo);
            return;
        }

        // 단일결제 order_no로 못 찾았으면 형제 묶음결제의 group_order_no일 수 있다.
        List<PaymentRespDTO.PaymentDTO> group = paymentRepository.findByGroupOrderNo(orderNo);
        boolean owns = group.stream().anyMatch(p -> p.getStudentId().equals(studentId));
        if (!owns) {
            return;   // 이미 없거나 남의 주문이면 조용히 무시한다
        }
        paymentTxService.confirmClosedGroup(orderNosOf(group));
        log.info("[결제] 결제창 이탈 정리(그룹) — groupOrderNo={}", orderNo);
    }

    /** 판매중인 상품 목록 (serviceCode가 null이면 전체) */
    public List<PaymentRespDTO.ProductDTO> products(String serviceCode) {
        return paymentRepository.findActiveProducts(serviceCode);
    }

    /** 결제 내역 — 로그인 학생 본인 것만 (형제 것은 여기 안 나온다) */
    public List<PaymentRespDTO.HistoryDTO> history(String studentId) {
        return paymentRepository.findHistory(List.of(studentId));
    }

    /**
     * 형제 묶음결제 건의 그룹원 조회 — 결제 내역에서 그룹 결제 하나를 "환불"하려 할 때,
     * 결제 화면의 형제 선택 체크박스와 똑같은 방식으로 "이 그룹 중 누구를 환불할지" 고르게
     * 하기 위해 그룹 전체(본인 포함)를 학생명과 함께 돌려준다.
     * 요청자가 그 그룹에 속해 있지 않으면(남의 그룹 주문번호를 넣어 엿보는 경로) 거부한다.
     */
    public List<PaymentRespDTO.HistoryDTO> groupMembers(String studentId, String groupOrderNo) {
        List<PaymentRespDTO.HistoryDTO> members = paymentRepository.findHistoryByGroupOrderNo(groupOrderNo);
        boolean requesterInGroup = members.stream().anyMatch(m -> m.getStudentId().equals(studentId));
        if (!requesterInGroup) {
            throw new Exception400("형제 관계가 아닌 결제입니다.");
        }
        return members;
    }

    // ───────────────────────────── 보조 ─────────────────────────────

    /**
     * 환불/견적 대상 결제 조회 — 형제 묶음결제 도입 이후로는 "본인 결제"가 아니라
     * "형제 그룹 소속 학생의 결제"면 된다. 결제도 형제 아무나 대신 진행할 수 있게 했으므로,
     * 환불도 같은 사람이 형제 대신 처리할 수 있어야 대칭이 맞는다. 형제가 없으면
     * findSiblingGroup이 본인 1건만 돌려주므로 기존과 동일하게 본인 것만 허용된다.
     */
    private PaymentRespDTO.PaymentDTO requireOwnPayment(String studentId, int paymentId) {
        PaymentRespDTO.PaymentDTO payment = paymentRepository.findById(paymentId);
        if (payment == null) {
            throw new Exception404("결제 정보를 찾을 수 없습니다.");
        }
        boolean inSiblingGroup = studentRepository.findSiblingGroup(studentId).stream()
                .anyMatch(s -> s.getStudentId().equals(payment.getStudentId()));
        if (!inSiblingGroup) {
            throw new Exception400("다른 학생의 결제입니다.");
        }
        return payment;
    }

    /**
     * 주문번호 — 이니시스 oid는 40자 제한이라 넉넉하지만, 짧고 사람이 읽을 수 있는 편이
     * 고객센터 문의 대응에 낫다. 접두어 + KST 시각 + 난수 6자리로 22자다.
     * 난수는 같은 초에 두 건이 들어와도 UNIQUE 제약에 걸리지 않게 하려는 것이다.
     */
    private String newOrderNo() {
        String time = LocalDateTime.now(KstClock.ZONE).format(ORDER_NO_TIME);
        int random = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return "BC" + time + random;
    }

    /**
     * 앱이 보낸 이니시스 주소인지 검증. 이걸 빼면 공격자가 자기 서버를 authUrl로 넣고
     * 위조된 성공 응답으로 이용권을 받아갈 수 있다.
     */
    private void assertInicisUrl(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || !host.endsWith(".inicis.com")) {
                throw new Exception400("허용되지 않은 결제 주소입니다.");
            }
        } catch (Exception400 e) {
            throw e;
        } catch (Exception e) {
            throw new Exception400("결제 주소가 올바르지 않습니다.");
        }
    }

    private PaymentRespDTO.ApproveDTO approveResult(PaymentRespDTO.PaymentDTO payment, int remain) {
        return new PaymentRespDTO.ApproveDTO(payment.getPaymentId(), payment.getOrderNo(),
                payment.getStatus(), payment.getAmount(), null, remain);
    }

    private int parseAmount(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return -1;   // 파싱 실패는 곧 금액 불일치로 취급된다(-1은 어떤 결제금액과도 같지 않다)
        }
    }

    /** 카드번호는 원본을 절대 저장하지 않는다. 앞 6자리 + 뒤 4자리만 남긴다 */
    private String maskCardNo(String cardNo) {
        if (cardNo == null || cardNo.length() < 10) {
            return cardNo;
        }
        return cardNo.substring(0, 6) + "******" + cardNo.substring(cardNo.length() - 4);
    }

    /**
     * 로그 원문 마스킹 — 응답 본문에 카드번호가 그대로 들어 있어 저장 전에 가린다.
     * 원문 보존과 개인정보 보호가 부딪히는 지점인데, 분쟁에 필요한 건 응답 구조와 결과 코드지
     * 카드번호 전체가 아니라서 마스킹 쪽을 택한다.
     */
    private String mask(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.replaceAll("(\\d{6})\\d{4,9}(\\d{4})", "$1******$2");
    }

    private String nvl(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    /** "202609" → "9월" — 결제창에 몇 월치 이용권인지 보여줄 때 쓴다 */
    private String monthLabel(String billingYm) {
        return Integer.parseInt(billingYm.substring(4)) + "월";
    }

    /** 결제 행에는 product_id만 있어서 서비스 구분이 필요할 때 상품을 되짚는다 */
    private String serviceCodeOf(PaymentRespDTO.PaymentDTO payment) {
        PaymentRespDTO.ProductDTO product = paymentRepository.findProductById(payment.getProductId());
        return product == null ? null : product.getServiceCode();
    }
}
