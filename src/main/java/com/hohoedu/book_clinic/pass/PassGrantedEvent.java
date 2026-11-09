package com.hohoedu.book_clinic.pass;

/**
 * 결제로 이용권이 새로 발급됐다 (2026-10-27).
 *
 * 앱 결제(PG)든 자동구매(빌키)든 발급은 {@link PassService#grantUnassigned} 한 곳을 지나므로 거기서 낸다.
 * 받는 쪽(AutoReservationService)은 결제 트랜잭션이 커밋된 뒤에 결제대기 예약을 확정하고, 이용권이
 * 없어 멈춰 있던 자동 예약을 이어 붙인다 — 결제 코드가 예약을 몰라도 되게 하려고 이벤트로 뗐다.
 */
public record PassGrantedEvent(String studentId) {
}
