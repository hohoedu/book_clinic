package com.hohoedu.book_clinic.payment;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hohoedu.book_clinic.payment._dto.PaymentAdminRespDTO;

/**
 * 관리자 "결제 내역" 화면 전용 매퍼 (2026-08-31).
 *
 * [PaymentRepository/PassRepository와 따로 두는 이유] 이 화면의 조회는 한 도메인에 속하지
 * 않는다 — 학생(erp_student)을 기준선으로 깔고 이용권(pass)·차감(pass_use)·결제(payment)·
 * 취소(payment_cancel)·예약(reservation/slot_instance)까지 가로지른다. 결제 매퍼에 넣으면
 * 결제 도메인이 예약 테이블을 알게 되고, 이용권 매퍼에 넣으면 "결제 방식 분기가 한 줄도 없다"는
 * PassRepository의 설계 의도가 깨진다. 화면이 소유하는 조회이므로 화면 단위로 분리한다.
 *
 * 읽기 전용이다. 이 화면에서 결제를 만들거나 되돌리는 경로는 없다(수기 등록은 미구현).
 */
@Mapper
public interface PaymentAdminRepository {

    /**
     * 목록 — 센터 재원생 전체를 기준선으로, 그 이용월(billingYm)의 이용권/결제를 붙인다.
     * 결제도 이용권도 없는 학생은 두 쪽이 전부 null인 행으로 나온다(= 미결제).
     * gradeKey/keyword는 null이면 조건에서 빠진다. 결제 상태 필터는 SQL이 아니라
     * PaymentAdminService가 계산한 뱃지 값으로 거른다(뱃지 판정이 Java에 있어서다).
     */
    List<PaymentAdminRespDTO.HistoryRowDTO> findHistoryRows(@Param("centerCode") String centerCode,
                                                            @Param("billingYm") String billingYm,
                                                            @Param("serviceCode") String serviceCode,
                                                            @Param("gradeKey") String gradeKey,
                                                            @Param("keyword") String keyword);

    /**
     * 차감 내역 — 이용일/회차/차감 후 잔여. 회차 시간은 그날 예약과 순번으로 짝지어 붙인다
     * (pass_use에 slot_instance_id가 없는 이유는 PaymentAdminRespDTO.PassUseRowDTO 주석 참고).
     */
    List<PaymentAdminRespDTO.PassUseRowDTO> findPassUses(@Param("passId") int passId,
                                                         @Param("studentId") String studentId);

    /** 결제/환불 내역 — 결제 1건과 그 건의 취소 이력을 시간 역순으로 합친다 */
    List<PaymentAdminRespDTO.PaymentTrailRowDTO> findPaymentTrail(@Param("paymentId") int paymentId);

    /** 상세 조회 전 소유 확인용 — 이 이용권이 그 학생 것인지 (다른 센터 학생 이용권 열람 차단) */
    String findPassOwner(@Param("passId") int passId);

    /** 상세 조회 전 소유 확인용 — 이 결제가 그 학생 것인지 */
    String findPaymentOwner(@Param("paymentId") int paymentId);
}
