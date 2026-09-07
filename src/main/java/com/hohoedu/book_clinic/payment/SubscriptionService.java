package com.hohoedu.book_clinic.payment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.handler.exception.Exception404;
import com.hohoedu.book_clinic._core.handler.exception.Exception500;
import com.hohoedu.book_clinic._core.utils.KstClock;
import com.hohoedu.book_clinic.clinic.ClinicRepository;
import com.hohoedu.book_clinic.pass.PassService;
import com.hohoedu.book_clinic.payment._dto.PaymentRespDTO;
import com.hohoedu.book_clinic.payment._dto.SubscriptionRespDTO;
import com.hohoedu.book_clinic.payment.inicis.InicisClient;
import com.hohoedu.book_clinic.payment.inicis.InicisProperties;
import com.hohoedu.book_clinic.student.StudentRepository;
import com.hohoedu.book_clinic.student.model.Student;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 자동결제(구독) — 카드등록 / 청구 / 해지. 2026-09-07 일시불에서 전환.
 *
 * [흐름]
 *   1) prepareCardReg  : 구독을 PENDING으로 선기록하고 빌키 발급 결제창 파라미터를 만든다(돈은 안 빠진다)
 *   2) completeCardReg : 결제창에서 돌아오면 승인해 CARD_BillKey를 받고, 곧바로 첫 청구를 낸다
 *   3) charge          : 빌키로 합산 승인 1건 → 학생별 payment + 이용권 발급
 *   4) 배치            : 매일 앵커일이 된 구독을 찾아 3)을 반복한다(SubscriptionBillingJob)
 *
 * [이 클래스가 지켜야 하는 것 — 이중 청구 금지]
 * 자동결제는 사람이 화면을 보고 있지 않은 곳에서 돈을 뺀다. 잘못되면 아무도 그 자리에서
 * 알아채지 못하므로, 방어선을 셋 겹쳐 둔다.
 *   ① 구독의 next_billing_on 조건부 UPDATE 선점(claimDue)
 *   ② 주기로부터 결정적으로 만든 주문번호(같은 주기 재청구는 PG가 중복 oid로 거절)
 *   ③ payment의 (student_id, service_code, cycle_from) 유니크 인덱스
 * 그리고 응답을 못 받은 건은 절대 실패로 단정하지 않는다 — 거래조회로 확인한다.
 *
 * [빌키] bill_key는 그 자체로 과금 권한이다. 로그·응답·화면 어디에도 내보내지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    /** 연속 실패 재시도 간격(일) — 3회까지 시도하고 그래도 안 되면 자동 중지 */
    private static final int[] RETRY_DAYS = {1, 3, 5};

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionTxService subscriptionTxService;
    private final PaymentRepository paymentRepository;
    private final ClinicRepository clinicRepository;
    private final StudentRepository studentRepository;
    private final PassService passService;
    private final InicisClient inicisClient;
    private final InicisProperties props;

    // ───────────────────────────── 카드 등록 ─────────────────────────────

    /**
     * 카드등록 시작 — 구독을 PENDING으로 선기록하고 빌키 발급 결제창 파라미터를 돌려준다.
     *
     * 결제창에서 우리 서버로 돌아오는 요청에는 세션 쿠키가 딸려오지 않는다(이니시스 도메인발
     * cross-site POST). 그래서 "누가 무엇을 등록하려 했는지"를 주문번호로만 되짚을 수 있고,
     * 그 정보를 미리 DB에 남겨야 한다 — 일반 결제가 READY 행을 먼저 만드는 것과 같은 이유다.
     */
    public SubscriptionRespDTO.CardRegDTO prepareCardReg(String ownerStudentId, List<String> studentIds,
                                                         String productCode) {
        if (studentIds == null || studentIds.isEmpty()) {
            throw new Exception400("자동결제를 등록할 학생을 선택해주세요.");
        }
        PaymentRespDTO.ProductDTO product = paymentRepository.findActiveProduct(productCode);
        if (product == null) {
            throw new Exception404("판매 중인 상품이 아닙니다.");
        }

        // 이미 자동결제가 걸려 있는 학생이 섞여 있으면 등록창을 띄우기 전에 막는다.
        // (동시 등록 레이스의 최종 판정은 등록 확정 시점의 유니크 인덱스가 한다)
        for (String studentId : studentIds) {
            SubscriptionRespDTO.SubscriptionDTO live =
                    subscriptionRepository.findLiveByMemberStudent(studentId, product.getServiceCode());
            if (live != null) {
                throw new Exception400("이미 자동결제가 등록된 학생이 있어요: " + studentName(studentId));
            }
        }

        String regOrderNo = PaymentService.newOrderNo();
        SubscriptionRespDTO.SubscriptionDTO row = new SubscriptionRespDTO.SubscriptionDTO();
        row.setRegOrderNo(regOrderNo);
        row.setOwnerStudentId(ownerStudentId);
        row.setCenterCode(clinicRepository.findCenterCode(ownerStudentId));
        subscriptionRepository.insertPending(row);

        int monthlyAmount = 0;
        for (String studentId : studentIds) {
            subscriptionRepository.insertMember(row.getSubscriptionId(), studentId,
                    product.getProductId(), product.getServiceCode());
            monthlyAmount += product.getPrice();
        }

        String goodName = product.getProductName()
                + (studentIds.size() > 1 ? " 외 " + (studentIds.size() - 1) + "명" : "") + " 자동결제";

        // 카드등록창이 닫힐 때 어느 등록이 취소됐는지 알아야 PENDING 행을 정리할 수 있다
        String closeUrl = props.getBillingCloseUrl() + "?regOrderNo=" + regOrderNo;
        return new SubscriptionRespDTO.CardRegDTO(regOrderNo, props.getBillingMid(), goodName,
                studentName(ownerStudentId), props.getBillingReturnUrl(), closeUrl,
                monthlyAmount, props.isTestMode());
    }

    /**
     * 카드등록 완료 — 결제창 인증 결과를 승인해 빌키를 받고, 곧바로 첫 청구까지 낸다.
     *
     * 등록만 해두고 청구를 다음 날 배치로 미루면 "카드는 등록됐는데 이용권이 없는" 하루가
     * 생긴다. 학부모 입장에서는 결제를 마친 것이므로 그 자리에서 이용권이 나와야 한다.
     * 그래서 등록일이 곧 앵커일이 되고, 첫 주기는 오늘부터 한 달이다.
     *
     * authUrl은 결제창이 보내온 값이라 그대로 믿으면 안 된다 — 이니시스 도메인인지 먼저 본다.
     */
    public SubscriptionRespDTO.CardRegResultDTO completeCardReg(String regOrderNo, String reqUrl, String tid) {
        PaymentService.assertInicisUrl(reqUrl);

        SubscriptionRespDTO.SubscriptionDTO sub = subscriptionRepository.findByRegOrderNo(regOrderNo);
        if (sub == null) {
            throw new Exception404("자동결제 등록 정보를 찾을 수 없습니다.");
        }
        if ("ACTIVE".equals(sub.getStatus()) || "PAUSED".equals(sub.getStatus())) {
            // 결제창에서 두 번 돌아온 경우. 다시 승인하지 않고 현재 상태를 그대로 돌려준다.
            return regResult(subscriptionRepository.findById(sub.getSubscriptionId()));
        }
        if (!"PENDING".equals(sub.getStatus())) {
            throw new Exception400("이미 종료된 카드등록입니다.");
        }

        InicisClient.Result result;
        try {
            result = inicisClient.approveMobileBilling(reqUrl, tid);
        } catch (Exception e) {
            // 빌키 발급은 돈이 빠지지 않으므로 되돌릴 승인이 없다 — 등록만 실패로 끝난다.
            log.error("[자동결제] 카드등록 승인 호출 실패 — regOrderNo={}", regOrderNo, e);
            paymentRepository.insertLog(regOrderNo, null, "BILL_AUTH", null, null, null, e.toString());
            throw new Exception500("카드 등록에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }

        paymentRepository.insertLog(regOrderNo, result.get("P_TID"), "BILL_AUTH",
                result.httpStatus(), result.get("P_STATUS"), null, PaymentService.mask(result.rawBody()));

        // 빌키 필드 이름은 매뉴얼 개정에 따라 갈릴 수 있어(모바일은 P_ 접두어, PC는 CARD_)
        // 후보를 순서대로 본다. 실 MID 연동 시 실제 응답으로 확인해 하나로 좁힌다.
        String billKey = firstNonBlank(result.get("P_BILLKEY"), result.get("CARD_BillKey"),
                result.get("P_CARD_BILLKEY"));
        if (!result.isMobileSuccess() || billKey == null) {
            throw new Exception400(nvl(result.get("P_RMESG1"), "카드 등록이 승인되지 않았습니다."));
        }

        LocalDate today = KstClock.today();
        boolean activated = subscriptionTxService.activate(sub.getSubscriptionId(), billKey,
                result.get("P_FN_NM"), PaymentService.maskCardNo(result.get("P_CARD_NUM")), today);
        if (!activated) {
            // 거의 동시에 두 번 돌아온 경우 — 먼저 확정한 쪽이 이겼다. 첫 청구는 그쪽이 낸다.
            log.info("[자동결제] 카드등록 중복 확정 요청 — regOrderNo={}", regOrderNo);
            return regResult(subscriptionRepository.findById(sub.getSubscriptionId()));
        }

        // 첫 청구. 실패해도 빌키는 살아 있으므로 구독을 지우지 않고 PAUSED로 남긴다 —
        // 학부모는 카드 한도/정지 같은 사유를 고친 뒤 앱에서 다시 시도할 수 있다.
        chargeNow(sub.getSubscriptionId(), today);
        return regResult(subscriptionRepository.findById(sub.getSubscriptionId()));
    }

    // ───────────────────────────── 청구 ─────────────────────────────

    /**
     * 지금 한 번 청구한다 — 첫 청구와 앱에서의 재시도가 함께 쓴다.
     * 배치는 선점(claimDue)을 거쳐 charge()를 직접 부른다.
     */
    public boolean chargeNow(int subscriptionId, LocalDate cycleFrom) {
        SubscriptionRespDTO.SubscriptionDTO sub = subscriptionRepository.findById(subscriptionId);
        if (sub == null) {
            throw new Exception404("자동결제 정보를 찾을 수 없습니다.");
        }
        List<SubscriptionRespDTO.MemberDTO> members =
                subscriptionRepository.findMembers(subscriptionId, "ACTIVE");
        if (members.isEmpty()) {
            throw new Exception400("청구할 학생이 없습니다.");
        }
        return charge(sub, members, cycleFrom);
    }

    /**
     * 빌키로 합산 승인 1건을 내고, 학생별 결제 행과 이용권으로 나눈다.
     *
     * [응답을 못 받으면] 실패로 단정하지 않는다. 카드사에는 승인이 나 있는데 우리만 모르는
     * 상태일 수 있고, 그대로 재청구하면 이중 청구가 된다. 거래조회로 실제 상태를 확인하고,
     * 그마저 안 되면 needs_review를 세워 사람이 보게 한다.
     *
     * @return 청구 성공 여부
     */
    private boolean charge(SubscriptionRespDTO.SubscriptionDTO sub,
                           List<SubscriptionRespDTO.MemberDTO> members, LocalDate cycleFrom) {
        LocalDate cycleUntil = PassService.cycleEnd(cycleFrom);
        String groupOrderNo = SubscriptionTxService.groupOrderNo(sub.getSubscriptionId(), cycleFrom);

        List<PaymentRespDTO.PaymentDTO> payments;
        try {
            payments = subscriptionTxService.openCharge(sub, members, cycleFrom, cycleUntil);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // 유니크 인덱스가 막았다 = 이 주기는 이미 청구돼 있다. 배치 중복 실행에서 정상적으로
            // 생기는 상황이라 실패로 취급하지 않고, 성공한 것으로 보고 다음 주기로 넘긴다.
            log.warn("[자동결제] 이미 청구된 주기 — subscriptionId={}, cycleFrom={}",
                    sub.getSubscriptionId(), cycleFrom, e);
            return true;
        }

        int totalPrice = members.stream().mapToInt(SubscriptionRespDTO.MemberDTO::getPrice).sum();
        String goodName = members.get(0).getProductName()
                + (members.size() > 1 ? " 외 " + (members.size() - 1) + "명" : "");
        Student owner = studentRepository.findById(sub.getOwnerStudentId());

        InicisClient.Result result;
        try {
            result = inicisClient.billing(sub.getBillKey(), groupOrderNo, totalPrice, goodName,
                    owner != null ? owner.getStudentName() : sub.getOwnerStudentId(),
                    owner != null ? owner.getBillingPhone() : null, null);
        } catch (Exception e) {
            log.error("[자동결제] 청구 호출 실패 — subscriptionId={}, cycleFrom={}",
                    sub.getSubscriptionId(), cycleFrom, e);
            paymentRepository.insertLog(groupOrderNo, null, "BILLING", null, null, null, e.toString());
            return settleUnknown(sub, payments, groupOrderNo, cycleFrom, e.toString());
        }

        paymentRepository.insertLog(groupOrderNo, result.get("tid"), "BILLING",
                result.httpStatus(), result.get("resultCode"), null, PaymentService.mask(result.rawBody()));

        if (!result.isBillingSuccess()) {
            String reason = nvl(result.get("resultMsg"), "카드 승인이 거절되었습니다.");
            fail(sub, payments, result.get("resultCode"), reason);
            return false;
        }

        // 위변조 검증 — 승인된 금액이 우리가 청구한 합계와 다르면 결제로 인정하지 않는다.
        // 자동결제에는 되돌릴 망취소 경로가 없으므로(결제창 인증이 없다) 사람이 상점관리자에서
        // 직접 취소해야 한다 — needs_review를 세워 그 사실이 묻히지 않게 한다.
        int approved = parseAmount(result.get("price"), result.get("TotPrice"));
        if (approved != totalPrice) {
            log.error("[자동결제] 승인 금액 불일치 — subscriptionId={}, 청구={}, 승인={}, tid={}",
                    sub.getSubscriptionId(), totalPrice, approved, result.get("tid"));
            markReview(payments, "자동결제 승인 금액 불일치 — 상점관리자에서 수동 취소 필요");
            fail(sub, payments, result.get("resultCode"), "승인 금액이 일치하지 않습니다.");
            return false;
        }

        try {
            subscriptionTxService.confirmCharged(payments, result.get("tid"), result.get("CARD_Name"),
                    PaymentService.maskCardNo(result.get("CARD_Num")), result.get("applNum"),
                    result.get("resultCode"));
        } catch (Exception e) {
            // 돈은 빠졌는데 우리 DB 확정에 실패한 상태 — 가장 위험한 지점이다. 자동으로 되돌릴
            // 수단이 없으므로(빌링에는 망취소가 없다) 반드시 사람 눈에 띄게 남긴다.
            log.error("[자동결제] 승인 확정 실패 — 수동 확인 필요. subscriptionId={}, tid={}",
                    sub.getSubscriptionId(), result.get("tid"), e);
            markReview(payments, "자동결제 승인 후 확정 실패 — 이용권 수동 발급 또는 취소 필요");
            return false;
        }

        subscriptionTxService.advanceCycle(sub.getSubscriptionId(), cycleUntil.plusDays(1));
        log.info("[자동결제] 청구 완료 — subscriptionId={}, 주기={}~{}, 금액={}",
                sub.getSubscriptionId(), cycleFrom, cycleUntil, totalPrice);
        return true;
    }

    /**
     * 응답을 못 받은 청구의 뒤처리 — 거래조회로 실제 승인 여부를 확인한다.
     * 확인이 되면 그 결과대로 확정하고, 조회마저 실패하면 사람이 보도록 남긴다.
     * 어느 쪽이든 "모르니까 일단 다시 청구"는 하지 않는다.
     */
    private boolean settleUnknown(SubscriptionRespDTO.SubscriptionDTO sub,
                                  List<PaymentRespDTO.PaymentDTO> payments, String groupOrderNo,
                                  LocalDate cycleFrom, String cause) {
        InicisClient.Result inquiry;
        try {
            inquiry = inicisClient.inquiryBilling(groupOrderNo);
        } catch (Exception e) {
            log.error("[자동결제] 응답 유실 후 거래조회도 실패 — 수동 확인 필요. subscriptionId={}, oid={}",
                    sub.getSubscriptionId(), groupOrderNo, e);
            markReview(payments, "자동결제 응답 유실 — 승인 여부 확인 필요");
            fail(sub, payments, null, "결제 응답을 확인하지 못했습니다.");
            return false;
        }

        if (inquiry.isInquirySuccess() && inquiry.isApproved()) {
            log.warn("[자동결제] 응답은 유실됐지만 승인은 나 있었다 — 복구 확정. subscriptionId={}, oid={}",
                    sub.getSubscriptionId(), groupOrderNo);
            try {
                subscriptionTxService.confirmCharged(payments, inquiry.get("tid"), inquiry.get("cardName"),
                        PaymentService.maskCardNo(inquiry.get("cardNumber")), inquiry.get("applNum"), "00");
                subscriptionTxService.advanceCycle(sub.getSubscriptionId(),
                        PassService.cycleEnd(cycleFrom).plusDays(1));
                return true;
            } catch (Exception e) {
                log.error("[자동결제] 유실 복구 확정 실패 — 수동 확인 필요. subscriptionId={}",
                        sub.getSubscriptionId(), e);
                markReview(payments, "자동결제 응답 유실분 복구 실패 — 이용권 수동 발급 필요");
                return false;
            }
        }

        // 조회는 됐고 승인은 없었다 = 진짜 실패다. 재시도해도 이중 청구가 아니다.
        fail(sub, payments, null, "결제가 승인되지 않았습니다. (" + cause + ")");
        return false;
    }

    /**
     * 실패 확정 — 결제 행을 닫고 재시도 일정을 잡는다.
     * RETRY_DAYS를 다 쓰면 자동 중지(PAUSED)하고 더 이상 카드를 긁지 않는다. 카드가 정지된
     * 상태로 매일 승인을 시도하면 카드사 쪽에 이상거래로 잡힐 수 있다.
     */
    private void fail(SubscriptionRespDTO.SubscriptionDTO sub, List<PaymentRespDTO.PaymentDTO> payments,
                      String resultCode, String reason) {
        int attempt = sub.getFailCount();   // 이번 실패까지 포함하면 attempt + 1회째다
        if (attempt >= RETRY_DAYS.length) {
            subscriptionTxService.confirmChargeFailed(sub.getSubscriptionId(), payments, resultCode,
                    sub.getNextBillingOn(), reason);
            subscriptionTxService.pause(sub.getSubscriptionId(), reason);
            log.warn("[자동결제] 연속 실패로 자동 중지 — subscriptionId={}, 사유={}",
                    sub.getSubscriptionId(), reason);
            return;
        }
        LocalDate retryOn = KstClock.today().plusDays(RETRY_DAYS[attempt]);
        subscriptionTxService.confirmChargeFailed(sub.getSubscriptionId(), payments, resultCode, retryOn, reason);
        log.warn("[자동결제] 청구 실패 — subscriptionId={}, {}회째, 재시도={}, 사유={}",
                sub.getSubscriptionId(), attempt + 1, retryOn, reason);
    }

    /** 사람이 상점관리자에서 직접 확인해야 하는 건으로 표시한다(운영 화면 /admin/payment/review-view) */
    private void markReview(List<PaymentRespDTO.PaymentDTO> payments, String reason) {
        for (PaymentRespDTO.PaymentDTO payment : payments) {
            try {
                paymentRepository.markNeedsReview(payment.getOrderNo(), reason);
            } catch (Exception e) {
                log.error("[자동결제] 확인 필요 표시 실패 — orderNo={}", payment.getOrderNo(), e);
            }
        }
    }

    // ───────────────────────────── 조회 / 해지 ─────────────────────────────

    /** 앱의 자동결제 상태 — 구독이 없으면 status=NONE으로 내려준다(화면이 등록 유도를 띄운다) */
    public SubscriptionRespDTO.StatusDTO status(String studentId) {
        SubscriptionRespDTO.SubscriptionDTO sub = subscriptionRepository.findLiveByMemberStudent(studentId, "BOOK");
        if (sub == null) {
            sub = subscriptionRepository.findLiveByOwner(studentId);
        }
        if (sub == null) {
            return new SubscriptionRespDTO.StatusDTO(null, "NONE", null, null, null, 0, List.of(), null);
        }

        List<SubscriptionRespDTO.MemberDTO> members =
                subscriptionRepository.findMembers(sub.getSubscriptionId(), "ACTIVE");
        List<SubscriptionRespDTO.MemberSummaryDTO> summaries = new ArrayList<>();
        int monthlyAmount = 0;
        for (SubscriptionRespDTO.MemberDTO member : members) {
            summaries.add(new SubscriptionRespDTO.MemberSummaryDTO(member.getStudentId(),
                    member.getStudentName(), member.getProductName(), member.getPrice()));
            monthlyAmount += member.getPrice();
        }

        return new SubscriptionRespDTO.StatusDTO(sub.getSubscriptionId(), sub.getStatus(),
                sub.getCardName(), sub.getCardNo(), sub.getNextBillingOn(), monthlyAmount, summaries,
                "PAUSED".equals(sub.getStatus()) ? sub.getLastFailReason() : null);
    }

    /**
     * 해지 — 다음 주기부터 청구하지 않는다. 이미 발급된 이용권은 그 주기 끝까지 그대로 쓴다
     * (돈을 받은 기간이므로 회수할 근거가 없다). 중도 환불이 필요하면 기존 환불 규정을 탄다.
     */
    public void cancel(String studentId, int subscriptionId) {
        requireOwn(studentId, subscriptionId);
        subscriptionTxService.cancel(subscriptionId);
        log.info("[자동결제] 해지 — subscriptionId={}, 요청자={}", subscriptionId, studentId);
    }

    /**
     * 중지된 자동결제 재시도 — 학부모가 카드 문제를 해결한 뒤 앱에서 직접 돌린다.
     *
     * 못 산 주기부터 다시 청구한다(billing_cycle_from은 실패 동안 그대로 보존돼 있다).
     * 자동 재개는 하지 않는다 — 카드가 계속 막힌 상태에서 배치가 매일 긁으면 카드사 쪽에
     * 이상거래로 잡힐 수 있어, 사람이 명시적으로 다시 시작하게 둔다.
     */
    public boolean retry(String studentId, int subscriptionId) {
        SubscriptionRespDTO.SubscriptionDTO sub = requireOwn(studentId, subscriptionId);
        if (!"PAUSED".equals(sub.getStatus())) {
            throw new Exception400("중지된 자동결제만 다시 시도할 수 있어요.");
        }
        LocalDate cycleFrom = sub.getBillingCycleFrom() != null ? sub.getBillingCycleFrom() : KstClock.today();
        subscriptionRepository.resume(subscriptionId, cycleFrom);
        return chargeNow(subscriptionId, cycleFrom);
    }

    /** 카드등록창까지 갔다가 이탈한 PENDING 정리 — 배치가 부른다 */
    public int closeStalePending(LocalDateTime cutoff) {
        return subscriptionRepository.closeStalePending(cutoff);
    }

    /** 오늘 청구할 구독 목록 — 배치가 부른다 */
    public List<SubscriptionRespDTO.SubscriptionDTO> findDue(LocalDate today) {
        return subscriptionRepository.findDue(today);
    }

    /**
     * 배치의 청구 1건 — 선점에 성공한 구독만 실제로 청구한다.
     *
     * 선점은 next_billing_on을 다음 앵커일로 미리 밀어두는 것이다. 실패하면 fail()이 재시도일로
     * 다시 당긴다. 순서를 반대로 하면(청구 먼저, 선점 나중) 배치가 두 번 뜬 순간 같은 주기가
     * 두 번 나간다.
     *
     * @return 청구를 시도했으면 true (선점 실패로 건너뛰었으면 false)
     */
    public boolean chargeDue(SubscriptionRespDTO.SubscriptionDTO sub) {
        LocalDate cycleFrom = sub.getBillingCycleFrom() != null ? sub.getBillingCycleFrom() : sub.getNextBillingOn();
        LocalDate nextAttempt = nextAnchorDate(cycleFrom, sub.getAnchorDay());

        int claimed = subscriptionRepository.claimDue(sub.getSubscriptionId(), sub.getNextBillingOn(), nextAttempt);
        if (claimed == 0) {
            log.info("[자동결제] 선점 실패(다른 실행이 처리 중) — subscriptionId={}", sub.getSubscriptionId());
            return false;
        }

        List<SubscriptionRespDTO.MemberDTO> members =
                subscriptionRepository.findMembers(sub.getSubscriptionId(), "ACTIVE");
        if (members.isEmpty()) {
            log.warn("[자동결제] 청구 대상 학생이 없어 중지 — subscriptionId={}", sub.getSubscriptionId());
            subscriptionTxService.pause(sub.getSubscriptionId(), "청구 대상 학생이 없습니다.");
            return false;
        }
        // 선점으로 next_billing_on이 바뀌었으므로, 실패 처리가 참조할 값도 갱신본이어야 한다
        sub.setNextBillingOn(nextAttempt);
        charge(sub, members, cycleFrom);
        return true;
    }

    /**
     * 앵커일 기준 다음 청구일 — 그 달에 앵커일이 없으면 말일로 당긴다(31일 앵커의 2월).
     * 앵커 자체는 구독에 보존되므로 다음 달에는 다시 31일로 돌아온다.
     */
    static LocalDate nextAnchorDate(LocalDate cycleFrom, Integer anchorDay) {
        LocalDate base = cycleFrom.plusMonths(1);
        if (anchorDay == null) {
            return base;
        }
        return base.withDayOfMonth(Math.min(anchorDay, base.lengthOfMonth()));
    }

    /**
     * 이 구독을 이 학생이 건드릴 수 있는지 — 대표 학생이거나 청구 대상 형제여야 한다.
     * 세션의 studentId는 컨트롤러가 이미 검증했으므로, 여기서 보는 것은 "그 학생의 구독인가"다.
     */
    private SubscriptionRespDTO.SubscriptionDTO requireOwn(String studentId, int subscriptionId) {
        SubscriptionRespDTO.SubscriptionDTO sub = subscriptionRepository.findById(subscriptionId);
        if (sub == null) {
            throw new Exception404("자동결제 정보를 찾을 수 없습니다.");
        }
        boolean isMember = subscriptionRepository.findMembers(subscriptionId, null).stream()
                .anyMatch(m -> m.getStudentId().equals(studentId));
        if (!sub.getOwnerStudentId().equals(studentId) && !isMember) {
            throw new Exception400("다른 사람의 자동결제입니다.");
        }
        return sub;
    }

    private SubscriptionRespDTO.CardRegResultDTO regResult(SubscriptionRespDTO.SubscriptionDTO sub) {
        return new SubscriptionRespDTO.CardRegResultDTO(sub.getSubscriptionId(), sub.getStatus(),
                sub.getCardName(), sub.getCardNo(), sub.getNextBillingOn(),
                passService.remain(sub.getOwnerStudentId(), "BOOK"));
    }

    private String studentName(String studentId) {
        Student student = studentRepository.findById(studentId);
        return student != null ? student.getStudentName() : studentId;
    }

    /** 빌링 응답의 금액 필드명이 매뉴얼 개정으로 갈릴 수 있어 두 이름을 모두 본다 */
    private int parseAmount(String primary, String fallback) {
        for (String raw : new String[]{primary, fallback}) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                return Integer.parseInt(raw.trim());
            } catch (NumberFormatException ignored) {
                // 다음 후보를 본다
            }
        }
        return -1;   // 어떤 청구 금액과도 같지 않은 값 → 금액 불일치로 처리된다
    }

    private String nvl(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) {
                return c;
            }
        }
        return null;
    }

    /** 카드등록창에서 이탈한 건 정리 — 결제의 abandon과 같은 역할이다 */
    public void abandonCardReg(String regOrderNo) {
        SubscriptionRespDTO.SubscriptionDTO sub = subscriptionRepository.findByRegOrderNo(regOrderNo);
        if (sub == null || !"PENDING".equals(sub.getStatus())) {
            return;
        }
        subscriptionTxService.cancel(sub.getSubscriptionId());
        log.info("[자동결제] 카드등록 이탈 — regOrderNo={}", regOrderNo);
    }
}
