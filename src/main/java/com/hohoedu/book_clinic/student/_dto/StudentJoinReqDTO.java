package com.hohoedu.book_clinic.student._dto;

import lombok.Data;

/**
 * 회원가입(입회) 폼 요청 — join.html 의 multipart form 필드와 1:1 (all_pass StudentJoinDTO + ParentJoinDTO 통합).
 * @ModelAttribute 로 바인딩되므로 기본 생성자/세터가 필요하다.
 */
@Data
public class StudentJoinReqDTO {

    // ----- 학생 -----
    private String studentName;
    private String birth;          // YYMMDD (서버에서 YYYY-MM-DD 로 변환)
    private String gender;         // "true"(남) / "false"(여)
    private String school;
    private String gradeKey;       // erp_bookstore_code gubun='S' 코드
    private String address;
    private String addressDetail;
    private String subject;        // "han" | "book" | "hoho"
    private boolean studentPrivacyAgree;

    private String centerCode;     // web=세션 센터(hidden), mobile=쿼리스트링(hidden)
    private String inviteCode;     // 현재 미사용 (pending 학생 연동은 이식 보류)

    // ----- 법정대리인(보호자) -----
    private String parentName;
    private String parentTelFirst;
    private String parentTelMiddle;
    private String parentTelLast;
    private String relationKey;
    private boolean parentPrivacyAgree;
}
