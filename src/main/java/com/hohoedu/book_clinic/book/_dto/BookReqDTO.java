package com.hohoedu.book_clinic.book._dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 도서 관련 요청 DTO 모음 */
public class BookReqDTO {

    /** 마스터 도서 등록 요청 */
    @Data
    public static class RegisterReqDTO {
        @NotBlank(message = "도서 제목은 필수입니다.")
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
        @NotNull(message = "도서 ID는 필수입니다.")
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
        @NotNull(message = "도서 ID는 필수입니다.")
        private Integer contentId;
    }

    /** 마스터 도서 복구 요청 */
    @Data
    public static class RestoreReqDTO {
        @NotNull(message = "삭제 이력 ID는 필수입니다.")
        private Integer delId;
    }

    /**
     * 실물 도서 등록 요청
     * ISBN 최초 등록 시 센터 매핑 정보(centerCode, quantity, state)도 함께 전달
     */
    @Data
    public static class ItemRegisterReqDTO {
        @NotBlank(message = "바코드(ISBN)는 필수입니다.")
        private String bcode;
        @NotNull(message = "마스터 도서 ID는 필수입니다.")
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
        @NotBlank(message = "바코드(ISBN)는 필수입니다.")
        private String bcode;
        private String bookTitle;
        private String publisher;
        private String keywords;
    }

    /** 실물 도서 삭제 요청 */
    @Data
    public static class ItemDeleteReqDTO {
        @NotBlank(message = "바코드(ISBN)는 필수입니다.")
        private String bcode;
    }

    /** 실물 도서 복구 요청 */
    @Data
    public static class ItemRestoreReqDTO {
        @NotNull(message = "삭제 이력 ID는 필수입니다.")
        private Integer delId;
    }

    /**
     * 센터 도서 매핑 등록 요청
     * 이미 등록된 ISBN을 다른 센터에서 보유할 때 사용
     */
    @Data
    public static class ItemCenterRegisterReqDTO {
        @NotBlank(message = "바코드(ISBN)는 필수입니다.")
        private String bcode;
        @NotBlank(message = "센터 코드는 필수입니다.")
        private String centerCode;
        private Integer quantity;
        private String state;
    }

    /** 센터 도서 수량/상태 수정 요청 */
    @Data
    public static class ItemCenterUpdateReqDTO {
        @NotBlank(message = "바코드(ISBN)는 필수입니다.")
        private String bcode;
        @NotBlank(message = "센터 코드는 필수입니다.")
        private String centerCode;
        private Integer quantity;
        private String state;
    }

}
