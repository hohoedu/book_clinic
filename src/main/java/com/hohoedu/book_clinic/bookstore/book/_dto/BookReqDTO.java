package com.hohoedu.book_clinic.bookstore.book._dto;

import lombok.Data;

/** 도서 관련 요청 DTO 모음 */
public class BookReqDTO {

    /** 마스터 도서 등록 요청 */
    @Data
    public static class RegisterReqDTO {
        private String title;
        private String author;
        private String genre;
        private String contentType;
        private String schoolYear;
        private String summary;
        private String keywords;
    }

    /** 마스터 도서 수정 요청 */
    @Data
    public static class UpdateReqDTO {
        private Integer contentId;
        private String title;
        private String author;
        private String genre;
        private String contentType;
        private String schoolYear;
        private String summary;
        private String keywords;
    }

    /** 마스터 도서 삭제 요청 */
    @Data
    public static class DeleteReqDTO {
        private Integer contentId;
    }

    /** 마스터 도서 복구 요청 */
    @Data
    public static class RestoreReqDTO {
        private Integer delId;
    }

    /**
     * 실물 도서 등록 요청
     * ISBN 최초 등록 시 센터 매핑 정보(centerCode, quantity, state)도 함께 전달
     */
    @Data
    public static class ItemRegisterReqDTO {
        private String bcode;
        private Integer contentId;
        private String bookTitle;
        private String publisher;
        private String keywords;
        private String centerCode;
        private Integer quantity;
        private String state;
    }

    /** 실물 도서 수정 요청 */
    @Data
    public static class ItemUpdateReqDTO {
        private String bcode;
        private String bookTitle;
        private String publisher;
        private String keywords;
    }

    /** 실물 도서 삭제 요청 */
    @Data
    public static class ItemDeleteReqDTO {
        private String bcode;
    }

    /** 실물 도서 복구 요청 */
    @Data
    public static class ItemRestoreReqDTO {
        private Integer delId;
    }

    /**
     * 센터 도서 매핑 등록 요청
     * 이미 등록된 ISBN을 다른 센터에서 보유할 때 사용
     */
    @Data
    public static class ItemCenterRegisterReqDTO {
        private String bcode;
        private String centerCode;
        private Integer quantity;
        private String state;
    }

    /** 센터 도서 수량/상태 수정 요청 */
    @Data
    public static class ItemCenterUpdateReqDTO {
        private String bcode;
        private String centerCode;
        private Integer quantity;
        private String state;
    }

    /** 센터 도서 매핑 삭제 요청 */
    @Data
    public static class ItemCenterDeleteReqDTO {
        private String bcode;
        private String centerCode;
    }

}
