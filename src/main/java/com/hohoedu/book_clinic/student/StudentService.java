package com.hohoedu.book_clinic.student;

import com.hohoedu.book_clinic._core.handler.exception.Exception404;
import com.hohoedu.book_clinic.clinic.ClinicService;
import com.hohoedu.book_clinic.clinic._dto.ClinicRespDTO;
import com.hohoedu.book_clinic.student._dto.StudentRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;
    private final ClinicService clinicService;

    /** "학생 정보" 화면 목록 — 센터 내 학생 전체 + 학생별 현재 레벨(학년별 기준권수 적용, ClinicService 계산 로직 재사용) */
    public List<StudentRespDTO.StudentInfoRowDTO> getStudentInfoList(String centerCode) {
        return getStudentInfoList(centerCode, null, null, null);
    }

    /** 위와 동일 + 학년/등록상태/검색어(이름 또는 연락처) 필터 */
    public List<StudentRespDTO.StudentInfoRowDTO> getStudentInfoList(String centerCode, String gradeKey, String statusKey, String keyword) {
        List<StudentRespDTO.StudentInfoRowDTO> students = studentRepository.findStudentByCenterCode(
                centerCode, blankToNull(gradeKey), blankToNull(statusKey), blankToNull(keyword));

        for (StudentRespDTO.StudentInfoRowDTO student : students) {
            ClinicRespDTO.MainLevelInfoDTO levelInfo = clinicService.getMainLevelInfo(student.getStudentId());
            student.setLevelNo(levelInfo.getLevelNo());
            student.setLevelTitle(levelInfo.getTitle());
        }

        return students;
    }

    /** "학생 정보" 필터바 학년 드롭다운 옵션 */
    public List<StudentRespDTO.GradeOptionDTO> getGradeOptions() {
        return studentRepository.findGradeOptions();
    }

    /** "학생 정보" 상세모달 — 기본 정보 + 레벨 */
    public StudentRespDTO.StudentDetailDTO getStudentDetail(String studentId) {
        StudentRespDTO.StudentDetailDTO detail = studentRepository.findStudentDetail(studentId);
        if (detail == null) {
            throw new Exception404("학생 정보를 찾을 수 없습니다: studentId=" + studentId);
        }
        ClinicRespDTO.MainLevelInfoDTO levelInfo = clinicService.getMainLevelInfo(studentId);
        detail.setLevelNo(levelInfo.getLevelNo());
        detail.setLevelTitle(levelInfo.getTitle());
        return detail;
    }

    /** "학생 정보" 상세모달 독서이력 탭 */
    public List<StudentRespDTO.ReadingHistoryRowDTO> getReadingHistory(String studentId) {
        return studentRepository.findReadingHistory(studentId);
    }

    /** "학생 정보" 상세모달 예약현황 탭 */
    public List<StudentRespDTO.ReservationHistoryRowDTO> getReservationHistory(String studentId) {
        return studentRepository.findReservationHistory(studentId);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
