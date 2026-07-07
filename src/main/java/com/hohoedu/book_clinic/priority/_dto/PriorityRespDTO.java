package com.hohoedu.book_clinic.priority._dto;

import java.time.LocalDateTime;

import lombok.Data;

/** 권장도서 순위 관련 응답 DTO 모음 */
public class PriorityRespDTO {

    /** 순위가 매겨진 도서 (화면 목록용) */
    @Data
    public static class RankedBookDTO {
        private Integer contentId;
        private String originalTitle;
        private String contentTypeName;
        private String keywords;
        private String difficulty;
        private String state;
        private String imageUrl;
        private Integer sortOrder; // null이면 아직 순위 미지정 (목록 뒤쪽에 붙음)
    }

    /** 순위 초안 (예약 목록 보기 화면용) */
    @Data
    public static class DraftDTO {
        private Integer draftId;
        private String year;
        private String schoolyear;
        private String isActive; // Y: 현재 적용 중인 초안
        private String createdBy;
        private LocalDateTime createdAt;
        private Integer bookCount;
    }

    /** 엑셀 다운로드용 순위 행 (연도의 활성 초안 전체 학년) */
    @Data
    public static class ExcelRowDTO {
        private String schoolyear;
        private String schoolyearName;
        private String contentTypeName;
        private String originalTitle;
        private String genreName;
        private String difficulty;
        private String curriculumName;    // content_detail gubun='C' (연계 교과)
        private String recommendOrgName;  // content_detail gubun='R' (추천기관)
        private String awardName;         // content_detail gubun='A' (수상명)
        private Integer sortOrder;
    }

}
