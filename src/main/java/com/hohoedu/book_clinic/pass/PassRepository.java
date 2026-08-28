package com.hohoedu.book_clinic.pass;

import java.time.LocalDate;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hohoedu.book_clinic.pass._dto.PassRespDTO;

/**
 * 이용권(erp_bookstore_pass) / 차감 이력(erp_bookstore_pass_use) 매퍼.
 *
 * 이 테이블들은 PG 결제와 서당 일괄청구가 함께 쓰는 공용 자산이라 결제 매퍼와 분리했다.
 * 차감 쪽 쿼리에 결제 방식 분기가 하나도 없는 것이 이 설계의 목적이다 —
 * 출석 코드는 이 학생에게 쓸 수 있는 이용권이 있는지만 알면 되고, 그게 카드로 산 것인지
 * 서당 청구분인지 알 필요가 없다.
 */
@Mapper
public interface PassRepository {

    /** 이용권 발급 — PG 승인 성공 / 서당 청구 확정 / 무상 부여가 모두 이 한 곳으로 들어온다 */
    void insertPass(@Param("studentId") String studentId, @Param("centerCode") String centerCode,
                    @Param("productId") int productId, @Param("serviceCode") String serviceCode,
                    @Param("source") String source, @Param("refNo") String refNo,
                    @Param("billingYm") String billingYm, @Param("validFrom") LocalDate validFrom,
                    @Param("validUntil") LocalDate validUntil, @Param("totalCount") int totalCount);

    /**
     * 이 학생의 이 서비스 이용권 중 가장 늦은 valid_until — 다음 결제의 대상월을 정할 때 쓴다.
     * 이미 지난 달이어도 상관없다(공백기 판단은 호출부가 한다), 살아있는(revoked_at NULL) 것만 본다.
     */
    LocalDate findLatestValidUntil(@Param("studentId") String studentId, @Param("serviceCode") String serviceCode);

    /**
     * 지금 쓸 수 있는 이용권 1건 — 오래 전에 받은 것부터 소진시킨다.
     * 이용권이 여러 장 겹칠 수 있어서(전월 잔여 + 이번 달분) 어느 것부터 깎을지 정해야 하는데,
     * 먼저 받은 것부터 쓰는 편이 사용자에게 유리하고 환불 계산도 단순해진다.
     */
    PassRespDTO.PassDTO findUsablePass(@Param("studentId") String studentId,
                                       @Param("serviceCode") String serviceCode);

    /**
     * 1회 차감. WHERE의 remain_count > 0 이 동시 요청에서 마이너스로 내려가는 것을 막는다
     * (조회 후 갱신 사이에 다른 요청이 끼어들어도 UPDATE가 0행이 되어 드러난다).
     */
    int decrementRemain(@Param("passId") int passId);

    /** 차감 이력. student_id + used_date UNIQUE라 같은 날 두 번째 시도는 예외로 튄다 */
    void insertUse(@Param("passId") int passId, @Param("studentId") String studentId,
                   @Param("sessionId") Integer sessionId, @Param("usedDate") LocalDate usedDate);

    /**
     * 오늘 이미 차감한 횟수(pass_use 행 수) — "그날 회차 수만큼 차감"(2026-08-28) 정책에서
     * 목표 차감수와 비교해 부족분만 채우는 데 쓴다. 재입실 시 이 값이 목표와 같거나 크면 추가 차감 없음.
     */
    int countTodayUse(@Param("studentId") String studentId, @Param("usedDate") LocalDate usedDate);

    /** 이 학생이 이 서비스에 쓸 수 있는 총 잔여 횟수 */
    int sumRemain(@Param("studentId") String studentId, @Param("serviceCode") String serviceCode);

    /**
     * 유효기간이 [monthStart, monthEnd]와 겹치는 살아있는 이용권의 total_count 합 —
     * 그 달 예약 상한 검사용(2026-08-28). 그 달 이용권이 없으면 0.
     */
    int sumMonthlyTotalCount(@Param("studentId") String studentId, @Param("serviceCode") String serviceCode,
                             @Param("monthStart") LocalDate monthStart, @Param("monthEnd") LocalDate monthEnd);

    /** 결제/청구 건으로 발급된 이용권 찾기 (환불 시 회수 대상) */
    PassRespDTO.PassDTO findByRef(@Param("source") String source, @Param("refNo") String refNo);

    /** 이 이용권에서 실제로 차감된 횟수 — 환불 규정의 "몇 회 썼는가" */
    int countUse(@Param("passId") int passId);

    /**
     * 이용권 회수 — 환불/청구취소로 무효화한다. 잔여를 0으로 만들면서 회수 시각을 남기는데,
     * 잔여만 0으로 만들면 "다 써서 0"인지 "환불돼서 0"인지 구분이 안 되기 때문이다.
     */
    int revoke(@Param("passId") int passId);
}
