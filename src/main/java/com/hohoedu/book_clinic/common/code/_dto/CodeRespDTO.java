package com.hohoedu.book_clinic.common.code._dto;

import lombok.Data;

/** 공통 코드 관련 응답 DTO 모음 */
public class CodeRespDTO {

    /** 공통 코드 조회 응답 */
    @Data
    public static class CodeDTO {
        private Integer codeId;
        private String groupCode;
        private String code;
        private String codeName;
        private Integer sortOrder;
        private Boolean useYn;
    }

}
