package com.hohoedu.book_clinic.common.code._dto;

import lombok.Data;

/** 공통 코드 관련 요청 DTO 모음 */
public class CodeReqDTO {

    /** 공통 코드 등록 요청 */
    @Data
    public static class RegisterReqDTO {
        private String groupCode;
        private String code;
        private String codeName;
        private Integer sortOrder;
    }

    /** 공통 코드 수정 요청 (코드명, 정렬순서, 사용여부 변경 가능) */
    @Data
    public static class UpdateReqDTO {
        private Integer codeId;
        private String codeName;
        private Integer sortOrder;
        private Boolean useYn;
    }

    /** 공통 코드 삭제 요청 */
    @Data
    public static class DeleteReqDTO {
        private Integer codeId;
    }

}
