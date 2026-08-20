package com.hohoedu.book_clinic.kiosk._dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 센터 기기 라이선스 요청 DTO 모음 (2026-08-20) */
public class KioskReqDTO {

    /** 발급 — 본사가 대상 센터를 지정해서 만든다 */
    @Data
    public static class IssueReqDTO {
        /** 비우면 "전 센터" 키 — 본사 테스트 태블릿용. 어느 센터 학생이든 처리할 수 있다 */
        private String centerCode;
        /** 폐기할 기기를 사람이 고르는 단서라 필수다 (예: "1층 출석 태블릿") */
        @NotBlank(message = "기기 이름을 입력해주세요.")
        private String label;
        /** 등록 가능한 기기 수. 생략하면 1대, 0이면 무제한(본사 테스트용) */
        private Integer deviceLimit;
    }

    /** 등록 — 센터 직원이 기기에 키를 입력한다. 하이픈/대소문자는 서버가 정규화한다 */
    @Data
    public static class RegisterReqDTO {
        @NotBlank(message = "라이선스 키를 입력해주세요.")
        private String licenseKey;
    }
}
