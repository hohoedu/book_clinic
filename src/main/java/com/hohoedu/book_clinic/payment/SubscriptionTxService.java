package com.hohoedu.book_clinic.payment;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hohoedu.book_clinic.payment._dto.PaymentRespDTO;
import com.hohoedu.book_clinic.payment._dto.SubscriptionRespDTO;

import lombok.RequiredArgsConstructor;

/**
 * 자동결제의 DB 작업만 모아둔 트랜잭션 경계 — PaymentTxService와 같은 이유로 분리했다.
 *
 * 빌링 승인 호출은 최대 15초가 걸리는 외부 통신이다. 그 앞뒤 DB 갱신을 한 메서드에
 * @Transactional로 묶으면 커넥션을 쥔 채 PG 응답을 기다리게 되고, 배치가 구독 수만큼
 * 그 짓을 반복하면 커넥션 풀이 마른다. 그래서 "PG를 부르기 전"과 "응답을 받은 뒤"를
 * 각각 짧은 트랜잭션으로 끊는다.
 *
 * PG를 부를지 말지, 응답을 믿을지는 SubscriptionService가 정한다.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionTxService {

    private static final DateTimeFormatter CYCLE_TAG = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter BILLING_YM = DateTimeFormatter.ofPattern("yyyyMM");

    private final PaymentRepository paymentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentTxService paymentTxService;

    /**
     * 청구 시작 — 멤버 수만큼 payment READY 행을 만들고 공통 group_order_no로 묶는다.
     *
     * [주문번호를 난수로 만들지 않는 이유] 자동결제는 사람이 보고 있지 않은 배치에서 일어나므로,
     * 같은 주기를 두 번 청구하는 사고를 코드 밖에서도 막아야 한다. moid를 주기로부터 결정적으로
     * 만들어 두면(SUB{id}-{yyyyMMdd}) 재시도 시 이니시스가 중복 주문번호로 거절해 준다.
     *
     * [형제가 한 명이어도 group_order_no를 채운다] 일반 결제에서는 단일결제면 null이지만,
     * 자동결제는 언제나 "합산 승인 1건 : payment N행" 구조다. 멤버가 한 명일 때만 모양이 달라지면
     * 승인 확정·환불 경로가 인원수에 따라 갈라진다.
     *
     * @return 만들어진 READY 행들. 이미 그 주기가 청구돼 있으면(유니크 위반) 예외가 그대로 올라간다
     */
    @Transactional
    public List<PaymentRespDTO.PaymentDTO> openCharge(SubscriptionRespDTO.SubscriptionDTO sub,
                                                      List<SubscriptionRespDTO.MemberDTO> members,
                                                      LocalDate cycleFrom, LocalDate cycleUntil) {
        String groupOrderNo = groupOrderNo(sub.getSubscriptionId(), cycleFrom);
        String billingYm = cycleFrom.format(BILLING_YM);

        List<PaymentRespDTO.PaymentDTO> opened = new ArrayList<>();
        int seq = 1;
        for (SubscriptionRespDTO.MemberDTO member : members) {
            String orderNo = groupOrderNo + "-" + seq++;
            paymentRepository.insertReady(orderNo, groupOrderNo, member.getStudentId(),
                    member.getCenterCode() != null ? member.getCenterCode() : sub.getCenterCode(),
                    member.getProductId(), member.getProductName(), member.getServiceCode(),
                    billingYm, cycleFrom, cycleUntil, sub.getSubscriptionId(), member.getPrice());
            opened.add(paymentRepository.findByOrderNo(orderNo));
        }
        return opened;
    }

    /**
     * 청구 성공 확정 — 멤버별로 결제를 PAID로 올리고 같은 트랜잭션에서 이용권을 발급한다.
     *
     * 형제가 서로 다른 상품을 살 수 있어 상품을 하나로 못 묶는다. 그래서 PaymentTxService의
     * 그룹 확정을 쓰지 않고 행마다 상품을 되짚어 확정한다 — 트랜잭션은 이 메서드가 열어두므로
     * 중간에 실패하면 전원이 함께 롤백된다("형제 중 한 명만 이용권을 받은" 상태를 만들지 않는다).
     *
     * @return 전원이 새로 확정됐으면 true. 한 명이라도 이미 PAID였으면 false(중복 청구 의심 신호)
     */
    @Transactional
    public boolean confirmCharged(List<PaymentRespDTO.PaymentDTO> payments, String tid, String cardName,
                                  String cardNo, String applNo, String resultCode) {
        boolean allUpdated = true;
        for (PaymentRespDTO.PaymentDTO payment : payments) {
            PaymentRespDTO.ProductDTO product = paymentRepository.findProductById(payment.getProductId());
            boolean updated = paymentTxService.confirmPaid(payment, product, tid, "Card",
                    cardName, cardNo, applNo, resultCode);
            allUpdated = allUpdated && updated;
        }
        return allUpdated;
    }

    /**
     * 청구 성공 후 구독 상태 갱신 — 다음 주기로 넘긴다.
     * 결제 확정과 트랜잭션을 나눈 이유는, 이용권까지 나간 뒤에 이 갱신이 실패하더라도 돈과
     * 이용권의 짝은 이미 맞아 있고 다음 주기 계산은 배치가 다시 시도하면 되기 때문이다.
     */
    @Transactional
    public void advanceCycle(int subscriptionId, LocalDate nextCycleFrom) {
        subscriptionRepository.markCharged(subscriptionId, nextCycleFrom);
    }

    /** 청구 실패 확정 — 결제 행을 FAILED로 닫고 구독에 재시도 일정을 남긴다 */
    @Transactional
    public void confirmChargeFailed(int subscriptionId, List<PaymentRespDTO.PaymentDTO> payments,
                                    String resultCode, LocalDate retryOn, String reason) {
        for (PaymentRespDTO.PaymentDTO payment : payments) {
            paymentRepository.markFailed(payment.getOrderNo(), resultCode);
        }
        subscriptionRepository.markChargeFailed(subscriptionId, retryOn, reason);
    }

    /** 연속 실패 한도 초과 — 자동 중지. 이미 발급된 이용권은 건드리지 않는다 */
    @Transactional
    public void pause(int subscriptionId, String reason) {
        subscriptionRepository.pause(subscriptionId, reason);
    }

    /** 등록 확정 — 빌키 저장과 멤버 승격을 한 트랜잭션으로 묶는다 */
    @Transactional
    public boolean activate(int subscriptionId, String billKey, String cardName, String cardNo,
                            LocalDate firstCycleFrom) {
        int updated = subscriptionRepository.activate(subscriptionId, billKey, cardName, cardNo,
                firstCycleFrom.getDayOfMonth(), firstCycleFrom, firstCycleFrom);
        if (updated == 0) {
            return false;
        }
        // 학생 중복 등록(같은 서비스로 두 구독) 유니크는 여기서 최종 판정된다 —
        // 위반이면 예외가 올라가 이 트랜잭션이 통째로 롤백되고, 빌키도 저장되지 않는다.
        subscriptionRepository.activateMembers(subscriptionId);
        return true;
    }

    /** 해지 — 구독과 멤버를 함께 내린다. 멤버를 남기면 유니크에 걸려 재등록이 막힌다 */
    @Transactional
    public void cancel(int subscriptionId) {
        subscriptionRepository.cancel(subscriptionId);
        subscriptionRepository.removeMembers(subscriptionId);
    }

    /** 청구 주문번호 — 주기로부터 결정적으로 만든다(같은 주기 재청구를 PG가 거절하게 하려고) */
    public static String groupOrderNo(int subscriptionId, LocalDate cycleFrom) {
        return "SUB" + subscriptionId + "-" + cycleFrom.format(CYCLE_TAG);
    }
}
