package com.hohoedu.book_clinic.priority._dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 권장도서 순위 관련 요청 DTO 모음 */
public class PriorityReqDTO {

    /** 순위 초안 저장 요청 — contentIds는 화면에 보이는 순서 그대로(=순위) 전달 */
    @Data
    public static class SaveDraftReqDTO {
        // 등록 후 MyBatis useGeneratedKeys로 채워짐
        private Integer draftId;
        @NotBlank(message = "연도는 필수입니다.")
        private String year;
        @NotBlank(message = "학년은 필수입니다.")
        private String schoolYear;
        @NotEmpty(message = "순위를 지정할 도서 목록이 필요합니다.")
        private List<Integer> contentIds;
        // true면 저장 즉시 적용(활성화), false/null이면 기존처럼 해당 연도+학년의 첫 저장일 때만 자동 적용
        private Boolean applyNow;
    }

    /** 초안 적용(선택) 요청 */
    @Data
    public static class ActivateDraftReqDTO {
        @NotNull(message = "초안 ID는 필수입니다.")
        private Integer draftId;
    }

    /** 초안 삭제 요청 — _del로 이관만 하고 복구는 지원하지 않음 */
    @Data
    public static class DeleteDraftReqDTO {
        @NotNull(message = "초안 ID는 필수입니다.")
        private Integer draftId;
    }

    /** content_id + 순위 한 쌍 (저장 시 내부적으로 사용) */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RankedItem {
        private Integer contentId;
        private Integer sortOrder;
    }

}
