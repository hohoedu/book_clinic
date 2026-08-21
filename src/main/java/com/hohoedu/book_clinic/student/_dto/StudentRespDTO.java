package com.hohoedu.book_clinic.student._dto;

import lombok.Data;

public class StudentRespDTO {

    /** "학생 정보" 화면 목록 1행 — levelNo/levelTitle은 매퍼가 아니라 서비스에서 ClinicService.getMainLevelInfo()로 채운다 */
    @Data
    public static class StudentInfoRowDTO {
        private String studentId;
        private String studentName;
        private String gradeName;
        private String billingPhone;
        private String registeredAt;
        private String lastVisitDate;
        private Integer totalDoneBooks;
        private String statusKey;
        private Integer levelNo;
        private String levelTitle;
    }
}
