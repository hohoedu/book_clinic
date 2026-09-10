package com.hohoedu.book_clinic.student;

import com.hohoedu.book_clinic.student._dto.StudentJoinReqDTO;
import com.hohoedu.book_clinic.student._dto.StudentRespDTO;
import com.hohoedu.book_clinic.student.model.Student;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StudentRepository {

    Student findById(@Param("studentId") String studentId);

    // ===== 회원가입(입회) — all_pass 이식 (2026-09-10) =====

    /** 동명이인 + 보호자 연락처(중간+끝) 중복 체크 */
    int countDuplicateOnJoin(@Param("studentName") String studentName,
            @Param("telMiddle") String telMiddle, @Param("telLast") String telLast);

    /** appId 접두어(연락처 중간+끝)로 시작하는 기존 appId 의 최대 접미 숫자 */
    Integer findMaxAppIdSuffix(@Param("prefix") String prefix);

    /** 학생 행 insert — studentId/appId/appPassword/birth 등은 서비스에서 채워 넣는다 */
    void insertOnJoin(@Param("s") StudentJoinReqDTO dto,
            @Param("studentId") String studentId, @Param("birth") String birth,
            @Param("gender") boolean gender, @Param("appId") String appId,
            @Param("appPassword") String appPassword, @Param("billingPhone") String billingPhone,
            @Param("statusKey") String statusKey,
            @Param("subHan") boolean subHan, @Param("subBook") boolean subBook,
            @Param("subHoho") boolean subHoho);

    /** 보호자(법정대리인) 행 insert */
    void insertGuardianOnJoin(@Param("s") StudentJoinReqDTO dto, @Param("studentId") String studentId);

    /** 가입 직후 업로드된 서명 이미지 URL 저장 */
    int updateGuardianSignature(@Param("studentId") String studentId, @Param("signatureUrl") String signatureUrl);

    Student findByAppId(@Param("appId") String appId);

    List<String> findTokensByCenter(@Param("centerCode") String centerCode);

    List<String> findAllTokens();

    /** 특정 센터 안에서 이름 또는 appId로 학생 검색 (예약 등록 화면의 학생 선택용, 최대 20건) */
    List<Student> searchByKeyword(@Param("centerCode") String centerCode, @Param("keyword") String keyword);

    /** 이 학생이 속한 형제 그룹 전체(본인 포함) — 결제창 형제 선택 화면용. 형제가 없으면 본인 1건만 돌아온다 */
    List<Student> findSiblingGroup(@Param("studentId") String studentId);

    /** "학생 정보" 화면 목록 — 센터 내 학생 전체 (레벨은 조회 후 서비스에서 채운다). 필터는 전부 선택값이면 null로 넘긴다 */
    List<StudentRespDTO.StudentInfoRowDTO> findStudentByCenterCode(@Param("centerCode") String centerCode,
            @Param("gradeKey") String gradeKey, @Param("statusKey") String statusKey, @Param("keyword") String keyword);

    /** "학생 정보" 화면 학년 필터 드롭다운 옵션 */
    List<StudentRespDTO.GradeOptionDTO> findGradeOptions();

    /** "학생 정보" 상세모달 — 기본 정보 + 통계(레벨 제외, ClinicService에서 채운다) */
    StudentRespDTO.StudentDetailDTO findStudentDetail(@Param("studentId") String studentId);

    /** "학생 정보" 상세모달 독서이력 탭 — 최근 읽은 순 */
    List<StudentRespDTO.ReadingHistoryRowDTO> findReadingHistory(@Param("studentId") String studentId);

    /** "학생 정보" 상세모달 예약현황 탭 — 최근 순 */
    List<StudentRespDTO.ReservationHistoryRowDTO> findReservationHistory(@Param("studentId") String studentId);
}
