package com.hohoedu.book_clinic.book._dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 도서 관련 요청 DTO 모음 */
public class BookReqDTO {

    /** 마스터 도서 등록 요청 */
    @Data
    public static class RegisterReqDTO {
        // 등록 후 MyBatis useGeneratedKeys로 채워짐 (genre를 content_detail에 함께 저장하기 위함)
        private Integer contentId;
        @NotBlank(message = "도서 제목은 필수입니다.")
        private String title;
        private String author;
        private String genre;
        private String contentType;
        private String schoolYear;
        private String summary;
        private String keywords;
        private String state;         // 사용여부 (Y: 사용중, N: 절판)
        private String publisher;     // 출판사
        private String imageUrl;      // 도서 이미지 경로
        private String readingTime;   // 독서 예상 시간
        private String difficulty;    // 난이도 (상/중/하)
        // 분류(contentType)가 교과연계/기관추천/인증수상작일 때만 의미 있는 부가 정보
        // (연계교과=gubun'C' / 추천기관명=gubun'R' / 수상명=gubun'A', 도서당 최대 1행)
        private String extraDetail;
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
        private String state;         // 사용여부 (Y: 사용중, N: 절판)
        private String publisher;     // 출판사
        private String imageUrl;      // 도서 이미지 경로
        private String readingTime;   // 독서 예상 시간
        private String difficulty;    // 난이도 (상/중/하)
        // 분류(contentType)가 교과연계/기관추천/인증수상작일 때만 의미 있는 부가 정보
        // (연계교과=gubun'C' / 추천기관명=gubun'R' / 수상명=gubun'A', 도서당 최대 1행)
        private String extraDetail;
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
     * 실물 도서(사본) 등록 요청 — quantity만큼 같은 bcode를 공유하는 사본 행을 센터에 등록한다
     * (2026-07-13 재설계: item_center 통합으로 센터 지정이 필수가 됨)
     */
    @Data
    public static class ItemRegisterReqDTO {
        // ISBN 대신 숫자 UUID를 서버에서 자동 생성하므로 필수 아님 (전달 시 그대로 사용)
        private String bcode;
        @NotNull(message = "마스터 도서 ID는 필수입니다.")
        private Integer contentId;
        private String bookTitle;
        private String author;
        private String publisher;
        private String imageUrl;
        @NotBlank(message = "센터 코드는 필수입니다.")
        private String centerCode;
        private Integer quantity;  // 등록할 사본 수 (기본 1)
    }

    /** 실물 도서 수정 요청 */
    @Data
    public static class ItemUpdateReqDTO {
        @NotBlank(message = "실물 도서 식별자(bcode)는 필수입니다.")
        private String bcode;
        private String bookTitle;
        private String author;
        private String publisher;
        private String imageUrl;
    }

    /** 실물 도서(센터 보유) 삭제 요청 — 해당 (bcode+센터)의 사본 전체를 삭제한다 */
    @Data
    public static class ItemDeleteReqDTO {
        @NotBlank(message = "실물 도서 식별자(bcode)는 필수입니다.")
        private String bcode;
        @NotBlank(message = "센터 코드는 필수입니다.")
        private String centerCode;
    }

    /** 실물 도서 복구 요청 */
    @Data
    public static class ItemRestoreReqDTO {
        @NotNull(message = "삭제 이력 ID는 필수입니다.")
        private Integer delId;
    }

    /**
     * 센터 도서 매핑 등록 요청 — 이미 등록된 바코드를 다른 센터에서도 보유할 때,
     * 그 바코드의 도서 정보를 그대로 복사해 quantity만큼 사본 행을 추가한다
     */
    @Data
    public static class ItemCenterRegisterReqDTO {
        @NotBlank(message = "바코드(ISBN)는 필수입니다.")
        private String bcode;
        @NotBlank(message = "센터 코드는 필수입니다.")
        private String centerCode;
        private Integer quantity;
    }

    /** 센터 보유 사본 수량 조정 요청 — 목표 수량이 현재보다 많으면 추가 등록, 적으면 대여 중이 아닌 사본부터 제거 */
    @Data
    public static class ItemCenterUpdateReqDTO {
        @NotBlank(message = "바코드(ISBN)는 필수입니다.")
        private String bcode;
        @NotBlank(message = "센터 코드는 필수입니다.")
        private String centerCode;
        @NotNull(message = "목표 수량은 필수입니다.")
        private Integer quantity;
    }

    /** 실물 도서 대여 요청 — 학생은 앱 로그인 ID(appId)로 지정 (내부적으로 studentId로 변환해 저장) */
    @Data
    public static class ItemLoanReqDTO {
        @NotBlank(message = "바코드(ISBN)는 필수입니다.")
        private String bcode;
        @NotBlank(message = "센터 코드는 필수입니다.")
        private String centerCode;
        @NotBlank(message = "학생 앱 ID는 필수입니다.")
        private String appId;
    }

    /** 실물 도서 반납 요청 */
    @Data
    public static class ItemReturnReqDTO {
        @NotNull(message = "대여 이력 ID는 필수입니다.")
        private Integer loanId;
    }

}
