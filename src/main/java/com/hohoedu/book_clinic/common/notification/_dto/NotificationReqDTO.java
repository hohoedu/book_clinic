package com.hohoedu.book_clinic.common.notification._dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

/**
 * 알림 발송 관련 요청 DTO 모음
 */
public class NotificationReqDTO {

    /**
     * 알림 발송 요청 DTO
     * targetType: STUDENT(단건) | CENTER(센터 전체) | ALL(전체)
     * targetValue: STUDENT면 studentId, CENTER면 centerCode, ALL이면 생략
     */
    @Getter
    public static class SendReqDTO {
        @NotBlank
        private String title;
        @NotBlank
        private String body;
        // STUDENT(단건) | CENTER(센터 전체) | ALL(전체)
        @NotBlank
        private String targetType;
        // targetType=STUDENT 이면 student id, CENTER 이면 centerCode
        private String targetValue;
    }
}
