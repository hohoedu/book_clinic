package com.hohoedu.book_clinic.student._dto;

import lombok.Data;

public class StudentRespDTO {

    /** "학생 정보" 화면 목록 1행 — levelNo/levelTitle은 매퍼가 아니라 서비스에서 ClinicService.getMainLevelInfo()로 채운다 */
    @Data
    public static class StudentInfoRowDTO {
        private String studentId;
        private String studentName;
        private String gradeKey;
        private String gradeName;
        private String billingPhone;
        private String registeredAt;
        private String lastVisitDate;
        private Integer totalDoneBooks;
        private String statusKey;
        private Integer levelNo;
        private String levelTitle;
    }

    /** 학년 필터 드롭다운용 코드 1건 (erp_bookstore_code gubun='S') */
    @Data
    public static class GradeOptionDTO {
        private String code;
        private String codeNm;
    }

    /**
     * "학생 정보" 상세모달 전체/기본정보 탭 — DB에 실제로 있는 필드만 채운다. 담당선생님/회비/학생과의
     * 관계처럼 DB에 대응 컬럼이 없는 값은 이 DTO에 없다(프론트는 그 항목만 계속 목업으로 표시한다).
     * levelNo/levelTitle은 서비스에서 ClinicService.getMainLevelInfo()로 채운다.
     */
    @Data
    public static class StudentDetailDTO {
        private String studentId;
        private String studentName;
        private String gradeKey;
        private String gradeName;
        private String statusKey;
        private String billingPhone;
        private String school;
        private String address;
        private String addressDetail;
        private String birth;
        private Boolean gender;
        private String registeredAt;
        private String lastVisitDate;
        private Integer totalDoneBooks;
        private Integer kingCount;
        private Integer badgeCount;
        private Integer levelNo;
        private String levelTitle;
    }

    /** 독서이력 탭 1행 — 그날 읽은 책(diary_detail) 기준, grade/status는 recommend_log 스냅샷 */
    @Data
    public static class ReadingHistoryRowDTO {
        private String recordDate;
        private String bookName;
        private Integer basicCorrectCnt;       // "처음 점수"
        private Integer basicFinalCorrectCnt;  // "최종 점수" — 재도전 반영 (2026-08-28)
        private Integer basicTotalCnt;
        private Integer advancedCorrectCnt;
        private Integer advancedTotalCnt;
        /** DONE / PENDING (recommend_log.status) */
        private String status;
        /** KING / FRIEND / null (recommend_log.grade) */
        private String grade;
        private String note;
    }

    /** 예약현황 탭 1행 */
    @Data
    public static class ReservationHistoryRowDTO {
        private String serviceDate;
        private Integer seq;
        /** RESERVED / CANCELED / ATTENDED / NOSHOW */
        private String status;
    }
}
