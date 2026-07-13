package com.hohoedu.book_clinic.book._dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 도서 관련 응답 DTO 모음 */
public class BookRespDTO {

    /** 마스터 도서 조회 응답 */
    @Data
    public static class ContentRespDTO {
        private Integer contentId;
        private String originalTitle;
        private String author;
        private String genre;
        private String genreName;
        private String contentType;
        private String contentTypeName;
        private String schoolyear;
        private String schoolyearName;
        private String summary;
        private String keywords;
        private String state;         // 사용여부 (Y: 사용중, N: 절판)
        private String publisher;     // 출판사
        private String imageUrl;      // 도서 이미지 경로
        private String readingTime;   // 독서 예상 시간
        private String difficulty;    // 난이도 (상/중/하)
        // 분류(contentType)에 딸린 부가 정보 (연계교과/추천기관명/수상명 중 해당 분류 값 하나, 도서당 최대 1행)
        private String extraDetailName;
    }

    /** 실물 도서(사본) 조회 응답 (2026-07-13 재설계: item_center 통합) */
    @Data
    public static class ItemRespDTO {
        private Integer itemId;
        private String bcode;
        private Integer contentId;
        private String bookTitle;
        private String author;
        private String publisher;
        private String imageUrl;
        private String centerCode;
        private String status;  // AVAILABLE / LOANED / LOST
    }

    /** 삭제된 실물 도서 조회 응답 */
    @Data
    public static class ItemDelRespDTO {
        private Integer delId;
        private LocalDateTime deletedAt;
        private String deletedBy;
        private Integer itemId;
        private String bcode;
        private Integer contentId;
        private String bookTitle;
        private String author;
        private String publisher;
        private String imageUrl;
        private String centerCode;
        private String status;
    }

    /**
     * 센터별 보유 도서(사본) 조회 응답 — 사본 1행 = 1건이라 quantity는 항상 1,
     * loanedQty/lostQty는 그 사본의 현재 상태(status)를 0/1로 나타낸다 (프런트 카드가 기대하는 필드 구조는 유지)
     */
    @Data
    public static class ItemCenterRespDTO {
        private String bcode;
        private String centerCode;
        private Integer quantity;
        private Integer loanedQty;
        private Integer lostQty;
        private String state;  // 사본 상태 스냅샷 (AVAILABLE/LOANED/LOST)
        private LocalDateTime registeredAt;
        private Integer contentId;
        private String bookTitle;
        private String author;
        private String publisher;
        private String imageUrl;
    }

    /** 실물 도서 대여 이력 조회 응답 (item_id 기준, bcode/centerCode는 조인으로 채워 화면 호환 유지) */
    @Data
    public static class ItemLoanRespDTO {
        private Integer loanId;
        private Integer itemId;
        private String bcode;
        private String centerCode;
        private String studentId;
        private String studentName;
        private LocalDateTime loanedAt;
        private LocalDateTime returnedAt;
        private String status;
    }

    /** 삭제된 마스터 도서 조회 응답 */
    @Data
    public static class ContentDelRespDTO {
        private Integer delId;
        private LocalDateTime deletedAt;
        private String deletedBy;
        private Integer contentId;
        private String originalTitle;
        private String author;
        private String genre;
        private String contentType;
        private String schoolyear;
        private String summary;
        private String keywords;
    }

}
