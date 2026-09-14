package com.hohoedu.book_clinic.pass;

import java.time.LocalDate;
import java.util.List;

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
     * 유효기간 확정 — 아직 미배정(valid_from IS NULL)인 이용권에만 기간을 박는다(2026-09-14).
     * 조건부 UPDATE라 두 요청이 동시에 들어와도 먼저 도착한 쪽만 1행을 받는다.
     *
     * @param startDate 첫 예약이 잡힌 회차의 날짜 (= valid_from)
     * @param addDays   valid_until까지 더할 일수 (90일 정책이면 89)
     */
    int activatePass(@Param("passId") int passId, @Param("startDate") LocalDate startDate,
                     @Param("addDays") int addDays);

    /**
     * 유효기간 해제 — 첫 예약이 취소되어 한 번도 쓰지 않은 상태로 되돌아간 이용권을 다시
     * 미배정으로 만든다. 살아있는 차감이 남아 있는 이용권에는 호출하지 않는다(호출부가 판단).
     */
    int deactivatePass(@Param("passId") int passId);

    /**
     * {@code date}에 쓸 수 있는 이용권 1건 — 먼저 만료되는 것부터 소진시킨다.
     * 이용권이 여러 장 겹칠 수 있어서(전 주기 잔여 + 이번 주기분) 어느 것부터 깎을지 정해야 하는데,
     * 먼저 만료되는 것부터 쓰는 편이 사용자에게 유리하다.
     *
     * 기준이 "오늘"이 아니라 파라미터인 이유는 차감 시점이 입실에서 예약으로 옮겨졌기 때문이다
     * (2026-09-14) — 9월 30일 회차를 9월 14일에 예약하면 9월 30일을 덮는 이용권에서 까야 한다.
     */
    PassRespDTO.PassDTO findUsablePassOn(@Param("studentId") String studentId,
                                         @Param("serviceCode") String serviceCode,
                                         @Param("date") LocalDate date);

    /**
     * 1회 차감. WHERE의 remain_count > 0 이 동시 요청에서 마이너스로 내려가는 것을 막는다
     * (조회 후 갱신 사이에 다른 요청이 끼어들어도 UPDATE가 0행이 되어 드러난다).
     */
    int decrementRemain(@Param("passId") int passId);

    /** 차감 이력 1행 = 예약 1건 = remain_count 1 감소 (2026-09-14 예약 시 차감) */
    void insertUse(@Param("passId") int passId, @Param("studentId") String studentId,
                   @Param("reservationId") Long reservationId, @Param("usedDate") LocalDate usedDate);

    /**
     * 그 예약으로 깐 살아있는(canceled_at IS NULL) 차감 이력 — 예약 취소 시 복구 대상.
     * 정상적으로는 예약 1건에 1행이지만, 과거 데이터나 재시도로 여러 행이 있어도 전부 되돌린다.
     */
    List<PassRespDTO.UseDTO> findLiveUsesByReservation(@Param("reservationId") Long reservationId);

    /**
     * 1회 복구 — 예약 취소로 차감을 되돌린다. remain_count가 total_count를 넘지 않도록
     * 조건을 걸어, 같은 예약에 복구가 두 번 들어와도 잔여가 부풀지 않는다.
     * 이미 회수(환불)된 이용권은 복구하지 않는다 — 환불로 0이 된 잔여가 되살아나면 안 된다.
     */
    int incrementRemain(@Param("passId") int passId);

    /** 차감 이력 무효화 — 복구된 행에 취소 시각을 찍는다. 이미 찍혀 있으면 0행(중복 복구 방지) */
    int markUseCanceled(@Param("useId") int useId);

    /** 이 학생이 이 서비스에 쓸 수 있는 총 잔여 횟수 */
    int sumRemain(@Param("studentId") String studentId, @Param("serviceCode") String serviceCode);

    /**
     * 그 날짜를 덮고 있는 살아있는 이용권들의 기간 합집합과 total_count / remain_count 합.
     *
     * 2026-09-07 자동결제 전환 전에는 달력 월과 겹치는 이용권을 합쳤는데(sumMonthlyTotalCount),
     * 주기가 결제일 기준 1개월이 되면서 한 달에 두 주기가 걸치게 됐다. 그대로 두면 그 달 상한이
     * 두 주기의 합이 되어 최대 두 배로 부풀어 오른다. 그래서 "그 달"이 아니라 "그 날짜가 속한
     * 주기"를 기준으로 바꾼다. 덮는 이용권이 없으면 capacity=0, 기간은 null이다.
     *
     * 2026-09-14 예약 시 차감 전환 이후 예약 가능 여부를 판정하는 값은 capacity(총량)가 아니라
     * remaining(잔여)이다 — 예약이 곧 차감이라 이미 잡아둔 예약은 잔여에서 빠져 있다.
     */
    PassRespDTO.CycleDTO findCycleOn(@Param("studentId") String studentId,
                                     @Param("serviceCode") String serviceCode,
                                     @Param("date") LocalDate date);

    /** 결제/청구 건으로 발급된 이용권 찾기 (환불 시 회수 대상) */
    PassRespDTO.PassDTO findByRef(@Param("source") String source, @Param("refNo") String refNo);

    /** 이 이용권에서 실제로 차감된 횟수 — 환불 규정의 "몇 회 썼는가". 취소로 되돌린 행은 세지 않는다 */
    int countUse(@Param("passId") int passId);

    /**
     * 이용권 회수 — 환불/청구취소로 무효화한다. 잔여를 0으로 만들면서 회수 시각을 남기는데,
     * 잔여만 0으로 만들면 "다 써서 0"인지 "환불돼서 0"인지 구분이 안 되기 때문이다.
     */
    int revoke(@Param("passId") int passId);
}
