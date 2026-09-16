package com.hohoedu.book_clinic.student._dto;

import lombok.Data;

public class StudentRespDTO {

    /** "학생 정보" 화면 목록 1행 — levelNo/levelTitle은 매퍼가 아니라 서비스에서 ClinicService.getMainLevelInfo()로 채운다 */
    @Data
    public static class StudentInfoRowDTO {
        private String studentId;
        private String studentName;
        /** 진짜 학년 코드 (erp_student.grade_key = 올패스 코드 05~07/11~16/21~23, erp_grade_code 와 조인) */
        private String gradeKey;
        /** 진짜 학년 표시명 ("초3", "중1", "5세" …) — 목록 "학년" 칸 */
        private String gradeName;
        /** 독서 학년 코드 (erp_student.clinic_grade_key = 클리닉 자체 코드 01~06 초1~초6 / 07 중등,
         *  erp_bookstore_code gubun='S' 와 조인). 레벨 앞 메달 이미지 medal_1~6.png 를 이 번호로 고른다 */
        private String clinicGradeKey;
        /** 독서 학년 표시명 ("초3" …) */
        private String clinicGradeName;
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
        /** 학년별 메달 이미지 경로 (초1~6만 존재, 중등/미지정이면 null — ClinicService.MEDAL_IMG_BY_SCHOOLYEAR) */
        private String medalImg;
    }

    /**
     * 상세모달 수강 정보 탭 (erp_bookstore_assign 1행, 2026-09-15).
     * 아직 저장한 적 없는 학생이면 서비스가 기본값(수강중 / 시작일=등록일 / 레벨=현재 자동레벨)으로
     * 채워 내려준다 — 이때 saved=false 라서 화면이 "아직 저장 전"임을 알 수 있다.
     */
    @Data
    public static class BookstoreAssignDTO {
        /** true=수강, false=미수강 */
        private Boolean state;
        private Integer eduFee;
        /** 저장 시점의 자동 계산 레벨 스냅샷 (1~30) */
        private Integer level;
        private String entryDate;
        private String inactiveDate;
        private String inactiveReason;
        /** erp_bookstore_assign 에 실제 행이 있는지 — false면 위 값들은 화면용 기본값이다 */
        private Boolean saved;
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
