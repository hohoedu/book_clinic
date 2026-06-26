package com.hohoedu.book_clinic.common.notification._dto;

import lombok.Getter;

import java.time.LocalDateTime;

import com.hohoedu.book_clinic.common.notification.model.Notification;

public class NotificationRespDTO {

    @Getter
    public static class SendResultDTO {
        private final int successCount;
        private final int failCount;

        public SendResultDTO(int successCount, int failCount) {
            this.successCount = successCount;
            this.failCount = failCount;
        }
    }

    @Getter
    public static class NotificationDTO {
        private final Integer id;
        private final LocalDateTime sentAt;
        private final String sentBy;
        private final String title;
        private final String body;
        private final String targetType;
        private final String targetId;
        private final String status;
        private final String errorMsg;

        public NotificationDTO(Notification n) {
            this.id = n.getId();
            this.sentAt = n.getSentAt();
            this.sentBy = n.getSentBy();
            this.title = n.getTitle();
            this.body = n.getBody();
            this.targetType = n.getTargetType();
            this.targetId = n.getTargetId();
            this.status = n.getStatus();
            this.errorMsg = n.getErrorMsg();
        }
    }
}
