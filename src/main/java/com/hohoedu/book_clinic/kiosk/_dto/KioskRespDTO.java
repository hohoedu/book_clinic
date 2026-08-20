package com.hohoedu.book_clinic.kiosk._dto;

import java.time.LocalDateTime;

import lombok.Data;

/** 센터 기기 라이선스 응답 DTO 모음 (2026-08-20) */
public class KioskRespDTO {

    /** 본사 기기 목록 한 줄 — 키 원문은 절대 포함하지 않는다(발급 응답에만 실린다) */
    @Data
    public static class LicenseDTO {
        private Integer licenseId;
        private String centerCode;
        private String centerName;
        private String label;
        private String issuedBy;
        private LocalDateTime issuedAt;
        /** 등록 가능한 기기 수. 0이면 무제한 */
        private Integer deviceLimit;
        /** 현재 등록되어 있는(폐기되지 않은) 기기 수 */
        private Integer deviceCount;
    }

    /** 이 키로 등록된 기기 1대 */
    @Data
    public static class DeviceDTO {
        private Integer deviceId;
        private String userAgent;
        private LocalDateTime registeredAt;
        private LocalDateTime lastUsedAt;
    }

    /**
     * 발급 응답 — 키 원문이 실리는 유일한 곳이다. DB에는 해시만 남으므로 이 응답을 놓치면
     * 다시 볼 수 없고, 폐기 후 재발급해야 한다.
     */
    @Data
    public static class IssuedDTO {
        private Integer licenseId;
        private String centerCode;
        private String label;
        /** 사람이 읽고 입력하는 형식 — XXXX-XXXX-XXXX-XXXX */
        private String licenseKey;
    }

    /** 발급 화면의 센터 드롭다운 항목 */
    @Data
    public static class CenterOptionDTO {
        private String centerCode;
        private String centerName;
    }

    /** 키 검사 결과 — 인터셉터가 쓰는 최소 정보 */
    @Data
    public static class ResolvedDTO {
        private Integer deviceId;
        private Integer licenseId;
        private String centerCode;
    }

    /** 등록 결과 — 기기 토큰 원문은 쿠키로만 나가고 화면에는 센터코드만 보여준다 */
    @Data
    public static class RegisteredDTO {
        private String centerCode;
        private String deviceToken;
    }
}
