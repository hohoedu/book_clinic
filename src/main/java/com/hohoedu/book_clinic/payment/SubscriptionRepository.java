package com.hohoedu.book_clinic.payment;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hohoedu.book_clinic.payment._dto.SubscriptionRespDTO;

/**
 * 자동결제(구독) 매퍼 — 2026-09-07 전환.
 *
 * 결제(payment)와 같은 패키지에 두는 이유는 둘이 한 트랜잭션에서 함께 움직이기 때문이다.
 * 구독은 "매달 계속 청구한다"는 상태이고 결제는 "이번 주기에 돈이 오갔다"는 사실이라 테이블은
 * 나뉘지만, 청구 한 번은 두 테이블을 같이 건드린다.
 */
@Mapper
public interface SubscriptionRepository {

    /**
     * 카드등록 시작 — 빌키가 아직 없는 PENDING 행을 먼저 남긴다.
     * 결제가 READY로 선기록되는 것과 같은 이유다. 등록창까지 갔다가 이탈한 건도 남아야
     * "왜 등록이 안 됐나"를 되짚을 수 있다.
     *
     * @return 생성된 subscription_id (row 객체에 채워진다)
     */
    void insertPending(SubscriptionRespDTO.SubscriptionDTO row);

    /** 청구 대상 학생 추가 — 등록 확정 전에는 PENDING 상태로 들어간다 */
    void insertMember(@Param("subscriptionId") int subscriptionId, @Param("studentId") String studentId,
                      @Param("productId") int productId, @Param("serviceCode") String serviceCode);

    SubscriptionRespDTO.SubscriptionDTO findByRegOrderNo(@Param("regOrderNo") String regOrderNo);

    SubscriptionRespDTO.SubscriptionDTO findById(@Param("subscriptionId") int subscriptionId);

    /**
     * 이 학생이 대표로 걸린 살아있는 구독(PENDING 제외) — 앱의 자동결제 상태 화면이 쓴다.
     * 형제 중 누구로 로그인해도 같은 구독을 보여줘야 하므로 멤버로도 찾는다(findByMemberStudent).
     */
    SubscriptionRespDTO.SubscriptionDTO findLiveByOwner(@Param("studentId") String studentId);

    /** 이 학생이 청구 대상으로 들어가 있는 살아있는 구독 */
    SubscriptionRespDTO.SubscriptionDTO findLiveByMemberStudent(@Param("studentId") String studentId,
                                                                @Param("serviceCode") String serviceCode);

    /** 구독의 멤버 목록 — 상품 마스터를 조인해 현재 가격/횟수까지 같이 가져온다 */
    List<SubscriptionRespDTO.MemberDTO> findMembers(@Param("subscriptionId") int subscriptionId,
                                                    @Param("status") String status);

    /**
     * 등록 확정 — 빌키를 저장하고 앵커일/다음 청구일을 세운다.
     * WHERE status='PENDING'이라 두 번 확정되지 않는다(결제창에서 두 번 돌아오는 경우).
     *
     * @return 갱신된 행 수. 0이면 이미 확정됐거나 닫힌 등록이다
     */
    int activate(@Param("subscriptionId") int subscriptionId, @Param("billKey") String billKey,
                 @Param("cardName") String cardName, @Param("cardNo") String cardNo,
                 @Param("anchorDay") int anchorDay, @Param("nextBillingOn") LocalDate nextBillingOn,
                 @Param("billingCycleFrom") LocalDate billingCycleFrom);

    /** 멤버를 청구 대상으로 올린다(PENDING → ACTIVE). 여기서 학생 중복 유니크가 최종 판정된다 */
    int activateMembers(@Param("subscriptionId") int subscriptionId);

    /**
     * 오늘 청구할 구독 — 시도일이 지났고 살아있는 것만.
     * 어제 이전 것까지 함께 집는 이유는 배치가 하루 걸러 떴을 때(장애·배포) 그날 건이 조용히
     * 건너뛰어지면 안 되기 때문이다.
     */
    List<SubscriptionRespDTO.SubscriptionDTO> findDue(@Param("today") LocalDate today);

    /**
     * 청구 선점 — 다음 시도일을 미리 밀어 다른 인스턴스가 같은 건을 집어가지 못하게 한다.
     * WHERE next_billing_on = 읽은 값이라, 0행이면 누가 먼저 가져간 것이다(건너뛴다).
     * 이것이 이중 청구 방어선의 1차다(2차는 결정적 moid, 3차는 payment 유니크 인덱스).
     */
    int claimDue(@Param("subscriptionId") int subscriptionId, @Param("expectedNextBillingOn") LocalDate expected,
                 @Param("newNextBillingOn") LocalDate newNextBillingOn);

    /**
     * 청구 성공 반영 — 다음 주기로 넘기고 실패 카운터를 되돌린다.
     * next_billing_on은 claimDue가 이미 밀어놨으므로 여기서는 주기 시작일만 옮긴다.
     */
    int markCharged(@Param("subscriptionId") int subscriptionId,
                    @Param("nextCycleFrom") LocalDate nextCycleFrom);

    /**
     * 청구 실패 반영 — 재시도일을 잡고 실패 횟수를 올린다.
     * billing_cycle_from은 건드리지 않는다. 재시도로 시도일이 밀려도 학생이 사는 기간은
     * 원래 주기 그대로여야 한다.
     */
    int markChargeFailed(@Param("subscriptionId") int subscriptionId, @Param("retryOn") LocalDate retryOn,
                         @Param("reason") String reason);

    /** 연속 실패 한도를 넘겨 자동 중지 */
    int pause(@Param("subscriptionId") int subscriptionId, @Param("reason") String reason);

    /**
     * 중지 해제 — 학부모가 카드 문제를 해결하고 다시 시도할 때. 실패 카운터를 0으로 돌려
     * 재시도 기회를 처음부터 다시 준다(안 그러면 한 번 실패하고 바로 다시 멈춘다).
     */
    int resume(@Param("subscriptionId") int subscriptionId, @Param("nextBillingOn") LocalDate nextBillingOn);

    /** 해지 — 구독과 멤버를 함께 내린다(멤버를 남기면 유니크에 걸려 재등록이 막힌다) */
    int cancel(@Param("subscriptionId") int subscriptionId);

    int removeMembers(@Param("subscriptionId") int subscriptionId);

    /**
     * 카드등록창까지 갔다가 이탈한 PENDING 건 정리 — 결제의 READY 정리와 같은 성격이다.
     * 빌키가 없으니 돈과는 무관하고, 남겨두면 그 학생이 재등록할 때 헷갈릴 뿐이다.
     */
    int closeStalePending(@Param("cutoff") java.time.LocalDateTime cutoff);
}
