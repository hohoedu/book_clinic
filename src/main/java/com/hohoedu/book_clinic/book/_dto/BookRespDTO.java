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
    }

    /** 실물 도서 조회 응답 (센터 매핑 정보 포함) */
    @Data
    public static class ItemRespDTO {
        private String bcode;
        private Integer contentId;
        private String bookTitle;
        private String publisher;
        private String keywords;
        private String centerCode;
        private String state;
    }

    /** 삭제된 실물 도서 조회 응답 */
    @Data
    public static class ItemDelRespDTO {
        private Integer delId;
        private LocalDateTime deletedAt;
        private String deletedBy;
        private String bcode;
        private Integer contentId;
        private String bookTitle;
        private String publisher;
        private String keywords;
    }

    /** 센터별 보유 도서 조회 응답 */
    @Data
    public static class ItemCenterRespDTO {
        private String bcode;
        private String centerCode;
        private Integer quantity;
        private String state;
        private LocalDateTime registeredAt;
        private Integer contentId;
        private String bookTitle;
        private String publisher;
        private String keywords;
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
