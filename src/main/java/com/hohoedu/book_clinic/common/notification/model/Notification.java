package com.hohoedu.book_clinic.common.notification.model;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 알림 발송 이력 도메인 모델 (erp_notification 테이블 매핑)
 */
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Notification {

    private Integer id;
    private LocalDateTime sentAt;
    private String sentBy;
    private String title;
    private String body;
    private String targetType;   // STUDENT | CENTER | ALL
    private String targetId;     // student_id (단건 발송일 때만)
    private String fcmToken;
    private String status;       // SUCCESS | FAIL
    private String errorMsg;

    @Builder
    public Notification(Integer id, LocalDateTime sentAt, String sentBy,
            String title, String body, String targetType, String targetId,
            String fcmToken, String status, String errorMsg) {
        this.id = id;
        this.sentAt = sentAt;
        this.sentBy = sentBy;
        this.title = title;
        this.body = body;
        this.targetType = targetType;
        this.targetId = targetId;
        this.fcmToken = fcmToken;
        this.status = status;
        this.errorMsg = errorMsg;
    }
}
