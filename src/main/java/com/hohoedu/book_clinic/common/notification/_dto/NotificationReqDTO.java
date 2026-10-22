package com.hohoedu.book_clinic.common.notification._dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알림 발송 관련 요청 DTO 모음
 */
public class NotificationReqDTO {

    /**
     * 알림 발송 요청 DTO
     * targetType: STUDENT(단건) | CENTER(센터 전체) | ALL(전체)
     * targetValue: STUDENT면 studentId, CENTER면 centerCode, ALL이면 생략
     */
    // 생성자 2종 — 요청 본문에서 만들어지는 길(기본 생성자)과, 서버가 직접 알림을 띄우는
    // 길(전체 생성자)이 둘 다 있다. 후자는 독서 결과 발송(AppSendService)이 쓴다 (2026-09-23).
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
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
        // 앱이 알림을 눌렀을 때 이동할 화면을 고르는 값(2026-10-22, 선택). 예: {type: BOOKSTORE_REPORT, studentId}
        // FCM data 는 문자열만 담을 수 있다.
        private Map<String, String> data;

        public SendReqDTO(String title, String body, String targetType, String targetValue) {
            this(title, body, targetType, targetValue, null);
        }
    }
}
