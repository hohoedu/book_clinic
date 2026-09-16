package com.hohoedu.book_clinic.student;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.handler.exception.Exception404;
import com.hohoedu.book_clinic.clinic.ClinicService;
import com.hohoedu.book_clinic.clinic._dto.ClinicRespDTO;
import com.hohoedu.book_clinic.student._dto.StudentReqDTO;
import com.hohoedu.book_clinic.student._dto.StudentRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;
    private final ClinicService clinicService;

    /** "학생 정보" 화면 목록 — 센터 내 학생 전체 + 학생별 현재 레벨(학년별 기준권수 적용, ClinicService 계산 로직 재사용) */
    public List<StudentRespDTO.StudentInfoRowDTO> getStudentInfoList(String centerCode) {
        return getStudentInfoList(centerCode, null, null, null);
    }

    /**
     * 위와 동일 + 학년/등록상태/검색어(이름 또는 연락처) 필터.
     * 레벨/칭호는 학생별로 따로 조회하지 않고 getMainLevelInfoBatch()로 한 번에 계산한다 — 학생 수만큼
     * 쿼리가 늘어나던 N+1을 없애 목록 조회가 학생 수와 무관하게 고정된 쿼리 수로 끝나게 한다(2026-09-15).
     */
    public List<StudentRespDTO.StudentInfoRowDTO> getStudentInfoList(String centerCode, String gradeKey, String statusKey, String keyword) {
        List<StudentRespDTO.StudentInfoRowDTO> students = studentRepository.findStudentByCenterCode(
                centerCode, blankToNull(gradeKey), blankToNull(statusKey), blankToNull(keyword));

        // Collectors.toMap은 값이 null이면 내부적으로 Map.merge에서 NPE가 나므로 null을 빈 문자열로 치환한다
        // (getMainLevelInfoBatch는 빈 문자열을 "clinic_grade_key 미배정"으로 보고 개별 조회로 대체한다)
        Map<String, String> clinicGradeKeyByStudentId = students.stream()
                .collect(Collectors.toMap(StudentRespDTO.StudentInfoRowDTO::getStudentId,
                        s -> s.getClinicGradeKey() == null ? "" : s.getClinicGradeKey()));
        Map<String, ClinicRespDTO.MainLevelInfoDTO> levelInfoByStudentId = clinicService.getMainLevelInfoBatch(clinicGradeKeyByStudentId);

        for (StudentRespDTO.StudentInfoRowDTO student : students) {
            ClinicRespDTO.MainLevelInfoDTO levelInfo = levelInfoByStudentId.get(student.getStudentId());
            if (levelInfo != null) {
                student.setLevelNo(levelInfo.getLevelNo());
                student.setLevelTitle(levelInfo.getTitle());
            }
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
        detail.setMedalImg(levelInfo.getMedalImg());
        return detail;
    }

    /**
     * "학생 정보" 상세모달 수강 정보 탭 — 아직 저장한 적 없는 학생이면 행을 만들지 않고
     * 화면용 기본값(수강중 / 시작일=학생 등록일 / 레벨=현재 자동계산 레벨)만 조립해 내려준다.
     */
    public StudentRespDTO.BookstoreAssignDTO getBookstoreAssign(String studentId) {
        StudentRespDTO.BookstoreAssignDTO assign = studentRepository.findBookstoreAssign(studentId);
        if (assign != null) {
            return assign;
        }

        StudentRespDTO.StudentDetailDTO detail = studentRepository.findStudentDetail(studentId);
        if (detail == null) {
            throw new Exception404("학생 정보를 찾을 수 없습니다: studentId=" + studentId);
        }

        StudentRespDTO.BookstoreAssignDTO fallback = new StudentRespDTO.BookstoreAssignDTO();
        fallback.setSaved(false);
        fallback.setState(true);
        fallback.setEntryDate(detail.getRegisteredAt());
        fallback.setLevel(clinicService.getMainLevelInfo(studentId).getLevelNo());
        return fallback;
    }

    /**
     * 수강 정보 저장(upsert). 레벨은 화면 입력이 아니라 저장 시점의 자동 계산 레벨을 서버가 찍는다.
     * 수강중이면 미수강 일자/사유는 남아있을 이유가 없으므로 지우고 저장한다(미수강 → 수강 복귀 케이스).
     */
    @Transactional
    public void saveBookstoreAssign(String studentId, StudentReqDTO.BookstoreAssignSaveDTO dto) {
        if (studentRepository.findStudentDetail(studentId) == null) {
            throw new Exception404("학생 정보를 찾을 수 없습니다: studentId=" + studentId);
        }
        if (dto.getState() == null) {
            throw new Exception400("수강 상태를 선택해주세요.");
        }
        if (blankToNull(dto.getEntryDate()) == null) {
            throw new Exception400("시작일자를 입력해주세요.");
        }
        if (dto.getEduFee() != null && dto.getEduFee() < 0) {
            throw new Exception400("교육비는 0원 이상이어야 합니다.");
        }

        dto.setEntryDate(blankToNull(dto.getEntryDate()));

        if (dto.getState()) {
            dto.setInactiveDate(null);
            dto.setInactiveReason(null);
        } else {
            dto.setInactiveDate(blankToNull(dto.getInactiveDate()));
            dto.setInactiveReason(blankToNull(dto.getInactiveReason()));
            if (dto.getInactiveReason() == null) {
                throw new Exception400("미수강 사유를 입력해주세요.");
            }
            if (dto.getInactiveDate() == null) {
                throw new Exception400("미수강 일자를 입력해주세요.");
            }
            if (dto.getInactiveDate().compareTo(dto.getEntryDate()) < 0) {
                throw new Exception400("미수강 일자는 시작일자보다 빠를 수 없습니다.");
            }
        }

        Integer level = clinicService.getMainLevelInfo(studentId).getLevelNo();
        studentRepository.saveBookstoreAssign(studentId, dto, level);
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
