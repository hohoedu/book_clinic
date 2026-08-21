package com.hohoedu.book_clinic.payment.job;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.hohoedu.book_clinic._core.utils.KstClock;
import com.hohoedu.book_clinic.payment.PaymentRepository;
import com.hohoedu.book_clinic.payment.PaymentTxService;
import com.hohoedu.book_clinic.payment._dto.PaymentRespDTO;
import com.hohoedu.book_clinic.payment.inicis.InicisClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * READY로 방치된 결제를 주기적으로 정리한다.
 *
 * [왜 필요한가] 결제창을 닫을 때 앱이 /payment/abandon을 호출하고, 이니시스 페이지 안에서
 * 취소해도 서버가 결과를 받아 정리하지만, 둘 다 "신호가 서버까지 도달해야" 동작한다.
 * 사용자가 앱을 강제 종료하거나 네트워크가 끊긴 채 나가면 그 신호 자체가 오지 않는다.
 * 그런 경우까지 잡는 마지막 방어선이라 클라이언트 신호와 무관하게 시간으로만 판단한다.
 *
 * [기준 시간] 승인 흐름(결제창 인증 → 서버 승인)이 정상적으로는 몇 분 안에 끝나므로,
 * 10분이 넘도록 READY인 건은 사실상 이탈로 본다. 실제 결제 흐름이 이보다 오래 걸릴
 * 사정이 생기면(예: 카드사 앱 인증이 오래 걸림) 이 값을 늘려야 한다.
 *
 * [2026-08-07 — 콜백 유실 대사] "10분 넘게 READY다 = 승인 시도조차 없었다"는 예전엔 그냥
 * 가정하고 바로 CLOSED로 닫았다. 그런데 이 가정이 항상 맞지는 않는다 — 카드사 승인은
 * 실제로 났는데 우리 서버로 오는 콜백(/payment/return)만 네트워크 문제 등으로 못 왔을
 * 수도 있고, 그러면 고객은 돈을 냈는데 우리 DB에서는 그냥 CLOSED로 조용히 지워진다.
 * 그래서 닫기 전에 이니시스 거래조회 API로 "진짜 승인 시도가 없었는지, 됐는데 콜백만
 * 못 받은 건지"를 먼저 확인하고, 후자면 CLOSED 대신 정상 승인 확정(PAID + 이용권 발급)
 * 경로로 복구한다.
 *
 * [형제 묶음결제도 같은 보호를 받는다(2026-08-07 추가)] 처음엔 "그룹은 개별 order_no로
 * 조회가 안 되니 범위 밖"이라고 건너뛰고 무조건 닫았는데, 그러면 그룹 결제만 콜백 유실
 * 보호가 안 되는 반쪽짜리 방어가 된다 — 그룹도 실제 돈이 오간 결제인 건 똑같다. PG 승인
 * 자체가 group_order_no를 oid로 나갔으므로, 그룹은 개별 행이 아니라 group_order_no
 * 단위로 딱 한 번만 조회하고, 승인됐으면 confirmPaidGroup으로 전원을 한 트랜잭션에 복구한다.
 *
 * [CLOSED로 닫은 직후의 레이스도 잡는다(2026-08-07 추가)] 고객이 이니시스 결제창에 들어간 채로
 * 10분 넘게 아무것도 안 하다가, 이 배치가 방금 위 로직으로 CLOSED 처리한 "바로 그 순간" 실제로
 * 승인을 완료해버릴 수 있다 — 카드사엔 승인이 남는데 우리는 이미 "종료됨"으로 확정해버린 것이다.
 * 그래서 실시간 승인 API(approve())는 그 순간엔 그대로 "이미 종료된 결제입니다"로 거절하되
 * (사용자에게 즉시, 명확하게 안내하는 게 맞다), 이 배치가 최근 CLOSED된 주문을 한 번 더
 * 사후 재확인해서, 실제로는 승인이었으면 PAID로 되돌린다. markPaid도 이 복구를 위해
 * WHERE status IN ('READY','CLOSED')로 완화해뒀다(PaymentMapper.xml 참고).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCleanupJob {

    private static final int STALE_MINUTES = 10;

    // CLOSED 사후 재확인 대상 범위 — 이보다 오래전에 닫힌 주문은 재확인하지 않는다(레이스는
    // 닫힌 직후에만 일어나므로, 오래된 CLOSED 건까지 매번 다시 조회하는 건 낭비다).
    private static final int CLOSED_RECHECK_WINDOW_HOURS = 2;

    private final PaymentRepository paymentRepository;
    private final PaymentTxService paymentTxService;
    private final InicisClient inicisClient;

    private enum Outcome { CLOSED, RECOVERED, PENDING }

    /** 5분마다 확인한다. 10분 기준보다 촘촘해야 방치 시간이 최대 15분을 넘지 않는다 */
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void cleanupStaleReady() {
        LocalDateTime cutoff = LocalDateTime.now(KstClock.ZONE).minusMinutes(STALE_MINUTES);
        List<PaymentRespDTO.PaymentDTO> staleReady = paymentRepository.findStaleReady(cutoff);
        if (staleReady.isEmpty()) {
            return;
        }

        // 그룹 묶음결제는 order_no가 아니라 group_order_no로 딱 한 번만 조회해야 한다(PG 승인
        // 자체가 group_order_no를 oid로 나갔으므로) — 같은 그룹에 속한 stale 행이 여러 개 섞여
        // 있어도 그룹당 조회는 한 번만 하도록 먼저 묶는다.
        List<PaymentRespDTO.PaymentDTO> solo = new ArrayList<>();
        Map<String, PaymentRespDTO.PaymentDTO> groupRepresentatives = new LinkedHashMap<>();
        for (PaymentRespDTO.PaymentDTO payment : staleReady) {
            if (payment.getGroupOrderNo() == null) {
                solo.add(payment);
            } else {
                groupRepresentatives.putIfAbsent(payment.getGroupOrderNo(), payment);
            }
        }

        int closed = 0;
        int recovered = 0;
        int pending = 0;
        for (PaymentRespDTO.PaymentDTO payment : solo) {
            switch (handleSolo(payment)) {
                case CLOSED -> closed++;
                case RECOVERED -> recovered++;
                case PENDING -> pending++;
            }
        }
        for (String groupOrderNo : groupRepresentatives.keySet()) {
            switch (handleGroup(groupOrderNo)) {
                case CLOSED -> closed++;
                case RECOVERED -> recovered++;
                case PENDING -> pending++;
            }
        }
        log.info("[결제] 방치 READY {}건(단일 {}건 + 그룹 {}건) 처리 — 닫음 {}건, 복구 {}건, 보류 {}건 (기준: {}분 경과)",
                staleReady.size(), solo.size(), groupRepresentatives.size(), closed, recovered, pending, STALE_MINUTES);

        recheckRecentlyClosed();
    }

    /**
     * 최근 CLOSED된 주문을 한 번 더 거래조회한다 — "닫은 직후 실제로는 승인됐던" 레이스를 잡는다.
     * 위 solo/group 처리와 로직 구조는 같지만, 이미 CLOSED된 것을 다시 CLOSED로 만들 필요는 없고
     * 승인이 확인될 때만 markPaid(READY/CLOSED 허용)를 태워 PAID로 되돌린다는 점만 다르다.
     */
    private void recheckRecentlyClosed() {
        LocalDateTime windowStart = LocalDateTime.now(KstClock.ZONE).minusHours(CLOSED_RECHECK_WINDOW_HOURS);
        List<PaymentRespDTO.PaymentDTO> candidates = paymentRepository.findClosedForRecheck(windowStart);
        if (candidates.isEmpty()) {
            return;
        }

        List<PaymentRespDTO.PaymentDTO> solo = new ArrayList<>();
        Map<String, PaymentRespDTO.PaymentDTO> groupRepresentatives = new LinkedHashMap<>();
        for (PaymentRespDTO.PaymentDTO payment : candidates) {
            if (payment.getGroupOrderNo() == null) {
                solo.add(payment);
            } else {
                groupRepresentatives.putIfAbsent(payment.getGroupOrderNo(), payment);
            }
        }

        int stillClosed = 0;
        int recovered = 0;
        int pending = 0;
        for (PaymentRespDTO.PaymentDTO payment : solo) {
            switch (recheckClosedSolo(payment)) {
                case CLOSED -> stillClosed++;
                case RECOVERED -> recovered++;
                case PENDING -> pending++;
            }
        }
        for (String groupOrderNo : groupRepresentatives.keySet()) {
            switch (recheckClosedGroup(groupOrderNo)) {
                case CLOSED -> stillClosed++;
                case RECOVERED -> recovered++;
                case PENDING -> pending++;
            }
        }
        log.info("[결제] CLOSED 사후 재확인 {}건(단일 {}건 + 그룹 {}건) 처리 — 그대로 확정 {}건, "
                        + "뒤늦은 승인 발견·복구 {}건, 보류 {}건",
                candidates.size(), solo.size(), groupRepresentatives.size(), stillClosed, recovered, pending);
    }

    private Outcome recheckClosedSolo(PaymentRespDTO.PaymentDTO payment) {
        InicisClient.Result result;
        try {
            result = inicisClient.inquiry(payment.getOrderNo());
        } catch (Exception e) {
            // 재조회 자체가 실패하면 판단을 못 내린 것 — closed_recheck_at을 안 찍어서 다음
            // 주기에 다시 시도한다.
            log.error("[결제] CLOSED 사후 재확인 조회 실패 — 다음 주기에 재시도. orderNo={}", payment.getOrderNo(), e);
            return Outcome.PENDING;
        }

        paymentRepository.insertLog(payment.getOrderNo(), result.get("tid"), "INQUIRY",
                result.httpStatus(), result.get("resultCode"), "CLOSED 사후 재확인", mask(result.rawBody()));

        if (!result.isInquirySuccess() || !result.isApproved()) {
            paymentRepository.markClosedRechecked(payment.getOrderNo());
            return Outcome.CLOSED;
        }

        log.warn("[결제] CLOSED로 닫았던 주문이 실제로는 승인된 거래였습니다 — 복구합니다. orderNo={}, tid={}",
                payment.getOrderNo(), result.get("tid"));
        Outcome outcome = recoverSolo(payment, result);
        if (outcome != Outcome.PENDING) {
            paymentRepository.markClosedRechecked(payment.getOrderNo());
        }
        return outcome;
    }

    private Outcome recheckClosedGroup(String groupOrderNo) {
        List<PaymentRespDTO.PaymentDTO> group = paymentRepository.findByGroupOrderNo(groupOrderNo);
        boolean allClosed = group.stream().allMatch(p -> "CLOSED".equals(p.getStatus()));
        if (!allClosed) {
            // 그룹 중 일부라도 이미 다른 상태(PAID 등)로 바뀌었으면 이 배치가 더 손댈 게 없다.
            group.forEach(p -> paymentRepository.markClosedRechecked(p.getOrderNo()));
            return Outcome.CLOSED;
        }

        InicisClient.Result result;
        try {
            result = inicisClient.inquiry(groupOrderNo);
        } catch (Exception e) {
            log.error("[결제] CLOSED 사후 재확인 조회 실패(그룹) — 다음 주기에 재시도. groupOrderNo={}", groupOrderNo, e);
            return Outcome.PENDING;
        }

        paymentRepository.insertLog(groupOrderNo, result.get("tid"), "INQUIRY",
                result.httpStatus(), result.get("resultCode"), "CLOSED 사후 재확인(그룹)", mask(result.rawBody()));

        if (!result.isInquirySuccess() || !result.isApproved()) {
            group.forEach(p -> paymentRepository.markClosedRechecked(p.getOrderNo()));
            return Outcome.CLOSED;
        }

        log.warn("[결제] CLOSED로 닫았던 그룹 주문이 실제로는 승인된 거래였습니다 — 복구합니다. groupOrderNo={}, tid={}",
                groupOrderNo, result.get("tid"));
        Outcome outcome = recoverGroup(group, result);
        if (outcome != Outcome.PENDING) {
            group.forEach(p -> paymentRepository.markClosedRechecked(p.getOrderNo()));
        }
        return outcome;
    }

    private Outcome handleSolo(PaymentRespDTO.PaymentDTO payment) {
        InicisClient.Result result;
        try {
            result = inicisClient.inquiry(payment.getOrderNo());
        } catch (Exception e) {
            // 조회 자체가 안 되면 승인 여부를 모른다는 뜻이다. 모르는 채로 닫으면 실제 승인된
            // 건을 놓칠 수 있으니, 이번 주기엔 그대로 두고(READY 유지) 다음 5분 주기에 재시도한다.
            log.error("[결제] 거래조회 실패 — 이번 주기엔 정리를 보류합니다. orderNo={}", payment.getOrderNo(), e);
            return Outcome.PENDING;
        }

        paymentRepository.insertLog(payment.getOrderNo(), result.get("tid"), "INQUIRY",
                result.httpStatus(), result.get("resultCode"), "방치 READY 자동조회", mask(result.rawBody()));

        if (!result.isInquirySuccess() || !result.isApproved()) {
            // 조회 결과 승인 시도가 없었거나(거래 없음) 취소된 거래 — 처음 가정대로 안전하게 닫는다.
            paymentTxService.confirmClosed(payment.getOrderNo());
            return Outcome.CLOSED;
        }

        return recoverSolo(payment, result);
    }

    /** 콜백을 못 받았을 뿐 실제로는 승인된 거래 — 정상 승인 확정 경로로 복구한다 */
    private Outcome recoverSolo(PaymentRespDTO.PaymentDTO payment, InicisClient.Result result) {
        int approvedAmount = parseAmount(result.get("price"));
        if (approvedAmount != payment.getAmount()) {
            // 금액이 다르면 이 주문의 승인이 아닐 수 있다 — 자동으로 확정하지 않고 사람이 보게 남긴다.
            log.error("[결제] 방치 READY 복구 중 금액 불일치 — 수동 확인 필요. orderNo={}, 기록={}, 조회={}",
                    payment.getOrderNo(), payment.getAmount(), approvedAmount);
            paymentRepository.markNeedsReview(payment.getOrderNo(),
                    "방치 READY 거래조회 결과 금액 불일치 — 이니시스 상점관리자에서 실제 승인 여부 확인 필요");
            return Outcome.PENDING;
        }

        PaymentRespDTO.ProductDTO product = paymentRepository.findProductById(payment.getProductId());
        if (product == null) {
            log.error("[결제] 방치 READY 복구 중 상품 정보 없음 — 수동 확인 필요. orderNo={}", payment.getOrderNo());
            paymentRepository.markNeedsReview(payment.getOrderNo(),
                    "방치 READY가 승인된 것으로 조회됐으나 상품 정보 없음 — 확인 필요");
            return Outcome.PENDING;
        }

        try {
            // cardName/cardNo/applNo는 거래조회 응답의 카드 상세 구조(cardInfo 등)가 매뉴얼에
            // 필드명까지 확정돼 있지 않아 비워둔다 — 결제내역 화면 표시용일 뿐 정합성과는 무관하다.
            paymentTxService.confirmPaid(payment, product, result.get("tid"),
                    nvl(result.get("paymethod"), "Card"), null, null, null, result.get("resultCode"));
        } catch (Exception e) {
            log.error("[결제] 방치 READY 복구 중 확정 실패 — 수동 확인 필요. orderNo={}, tid={}",
                    payment.getOrderNo(), result.get("tid"), e);
            paymentRepository.markNeedsReview(payment.getOrderNo(),
                    "방치 READY가 승인된 것으로 조회됐으나 확정 실패 — 확인 필요");
            return Outcome.PENDING;
        }

        log.info("[결제] 방치 READY 복구 완료 — 콜백 유실 건을 거래조회로 찾아 승인 처리했습니다. orderNo={}, tid={}",
                payment.getOrderNo(), result.get("tid"));
        return Outcome.RECOVERED;
    }

    private Outcome handleGroup(String groupOrderNo) {
        // findStaleReady에 걸린 행만이 아니라 그룹 전체를 다시 받는다 — 그룹원 중 일부만 stale
        // 목록에 우연히 걸렸을 가능성까지 배제하고 항상 그룹 전원을 기준으로 판단하기 위함이다.
        List<PaymentRespDTO.PaymentDTO> group = paymentRepository.findByGroupOrderNo(groupOrderNo);
        boolean allReady = group.stream().allMatch(p -> "READY".equals(p.getStatus()));
        if (!allReady) {
            // 이 배치가 판단을 내리기 전에 다른 경로(앱의 뒤늦은 승인 재시도 등)로 이미 처리가
            // 끝났다는 뜻 — 여기서 또 손대지 않는다.
            log.info("[결제] 방치 READY 그룹 재확인 — 이미 다른 경로로 처리됨, 건너뜀. groupOrderNo={}", groupOrderNo);
            return Outcome.CLOSED;
        }

        InicisClient.Result result;
        try {
            result = inicisClient.inquiry(groupOrderNo);
        } catch (Exception e) {
            log.error("[결제] 거래조회 실패(그룹) — 이번 주기엔 정리를 보류합니다. groupOrderNo={}", groupOrderNo, e);
            return Outcome.PENDING;
        }

        paymentRepository.insertLog(groupOrderNo, result.get("tid"), "INQUIRY",
                result.httpStatus(), result.get("resultCode"), "방치 READY 자동조회(그룹)", mask(result.rawBody()));

        if (!result.isInquirySuccess() || !result.isApproved()) {
            paymentTxService.confirmClosedGroup(orderNosOf(group));
            return Outcome.CLOSED;
        }

        return recoverGroup(group, result);
    }

    private Outcome recoverGroup(List<PaymentRespDTO.PaymentDTO> group, InicisClient.Result result) {
        String groupOrderNo = group.get(0).getGroupOrderNo();
        int recordedAmount = group.stream().mapToInt(PaymentRespDTO.PaymentDTO::getAmount).sum();
        int approvedAmount = parseAmount(result.get("price"));
        if (approvedAmount != recordedAmount) {
            log.error("[결제] 방치 READY 복구 중 금액 불일치(그룹) — 수동 확인 필요. groupOrderNo={}, 기록={}, 조회={}",
                    groupOrderNo, recordedAmount, approvedAmount);
            markNeedsReviewAll(group, "방치 READY 거래조회 결과 금액 불일치(그룹) — 이니시스 상점관리자에서 실제 승인 여부 확인 필요");
            return Outcome.PENDING;
        }

        PaymentRespDTO.ProductDTO product = paymentRepository.findProductById(group.get(0).getProductId());
        if (product == null) {
            log.error("[결제] 방치 READY 복구 중 상품 정보 없음(그룹) — 수동 확인 필요. groupOrderNo={}", groupOrderNo);
            markNeedsReviewAll(group, "방치 READY가 승인된 것으로 조회됐으나 상품 정보 없음(그룹) — 확인 필요");
            return Outcome.PENDING;
        }

        try {
            paymentTxService.confirmPaidGroup(group, product, result.get("tid"),
                    nvl(result.get("paymethod"), "Card"), null, null, null, result.get("resultCode"));
        } catch (Exception e) {
            log.error("[결제] 방치 READY 복구 중 확정 실패(그룹) — 수동 확인 필요. groupOrderNo={}, tid={}",
                    groupOrderNo, result.get("tid"), e);
            markNeedsReviewAll(group, "방치 READY가 승인된 것으로 조회됐으나 확정 실패(그룹) — 확인 필요");
            return Outcome.PENDING;
        }

        log.info("[결제] 방치 READY 복구 완료(그룹) — 콜백 유실 건을 거래조회로 찾아 승인 처리했습니다. groupOrderNo={}, tid={}",
                groupOrderNo, result.get("tid"));
        return Outcome.RECOVERED;
    }

    private List<String> orderNosOf(List<PaymentRespDTO.PaymentDTO> payments) {
        return payments.stream().map(PaymentRespDTO.PaymentDTO::getOrderNo).toList();
    }

    private void markNeedsReviewAll(List<PaymentRespDTO.PaymentDTO> group, String reason) {
        for (String orderNo : orderNosOf(group)) {
            paymentRepository.markNeedsReview(orderNo, reason);
        }
    }

    private int parseAmount(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return -1;   // 파싱 실패는 금액 불일치로 취급된다(-1은 어떤 결제금액과도 같지 않다)
        }
    }

    private String nvl(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    /** 로그 원문 마스킹 — 카드번호가 섞여 들어올 수 있어 저장 전에 가린다(PaymentService.mask와 동일 규칙) */
    private String mask(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.replaceAll("(\\d{6})\\d{4,9}(\\d{4})", "$1******$2");
    }
}
