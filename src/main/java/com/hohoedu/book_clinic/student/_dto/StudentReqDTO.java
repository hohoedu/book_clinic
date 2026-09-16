package com.hohoedu.book_clinic.student._dto;

import lombok.Data;

public class StudentReqDTO {

    /**
     * "학생 정보" 상세모달 수강 정보 탭 저장 요청 (2026-09-15).
     * 레벨은 저장 시점의 자동 계산값을 서버가 직접 스냅샷으로 찍으므로 여기에 없다 — 화면도 읽기 전용이다.
     * 미수강(state=false)일 때만 inactiveDate/inactiveReason 이 의미를 갖고, 수강중이면 서비스가 둘 다 지운다.
     */
    @Data
    public static class BookstoreAssignSaveDTO {
        /** true=수강, false=미수강 */
        private Boolean state;
        /** 교육비(원). 비워서 보내면 NULL 로 저장된다 */
        private Integer eduFee;
        /** 시작일자 (yyyy-MM-dd) */
        private String entryDate;
        /** 미수강 전환일 (yyyy-MM-dd) — state=false 일 때 필수 */
        private String inactiveDate;
        /** 미수강 사유 — state=false 일 때 필수 */
        private String inactiveReason;
    }
}
