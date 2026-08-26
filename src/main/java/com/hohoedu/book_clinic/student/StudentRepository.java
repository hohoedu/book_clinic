package com.hohoedu.book_clinic.student;

import com.hohoedu.book_clinic.student._dto.StudentRespDTO;
import com.hohoedu.book_clinic.student.model.Student;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StudentRepository {

    Student findById(@Param("studentId") String studentId);

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
