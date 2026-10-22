package com.hohoedu.book_clinic.monitor;

import java.time.LocalDateTime;

/**
 * 입실·퇴실이 확정됐다는 이벤트 (2026-10-22) — 학부모 앱 푸시용.
 *
 * MonitorService 가 트랜잭션 안에서 발행하고, AttendancePushListener 가 커밋된 뒤에만 받아 푸시를 보낸다.
 * 예약이 없어 입실이 롤백되면 이벤트도 버려져 "입실했어요" 알림이 잘못 나가지 않는다.
 *
 * @param entered true=입실, false=퇴실
 * @param at      입실/퇴실 시각(KST)
 */
public record AttendancePushEvent(String studentId, boolean entered, LocalDateTime at) { 
}
