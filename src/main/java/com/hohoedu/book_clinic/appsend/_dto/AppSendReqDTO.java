package com.hohoedu.book_clinic.appsend._dto;

import java.util.List;

import lombok.Data;

/** 독서 결과 발송 요청 DTO (2026-09-23) */
public class AppSendReqDTO {

    /**
     * 발송 처리 대상.
     *
     * 예약(reservationId)이 아니라 일지 키로 받는다 — 발송 상태가 붙는 자리가
     * erp_bookstore_diary 이고, 미입실이라 일지가 없는 행은 애초에 발송할 것이 없다.
     * 그런 행은 화면에서 걸러 보낸다.
     */
    @Data
    public static class SendDTO {
        private List<Integer> diaryKeys;
    }
}
