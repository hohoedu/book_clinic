package com.hohoedu.book_clinic.app._dto;

import lombok.Data;

/**
 * i-with 앱 화면 조회 요청 모음.
 *
 * 어느 요청에서도 studentId 는 받지 않는다 — 세션 값만 쓴다(AppController 주석 참고).
 */
public class AppReqDTO {

    /**
     * 정독 결과 화면 조회.
     *
     * recordDate 는 상단 일자 탭의 선택값이고, 첫 진입이라 비어 있으면 서버가 최근 일자를 고른다.
     * 탭에 없는 날짜가 와도 그 날짜로 그냥 조회한다(빈 결과가 나올 뿐이라 따로 막지 않는다).
     */
    @Data
    public static class BookstoreReportDTO {
        private String recordDate; // yyyy-MM-dd, nullable
    }
}
