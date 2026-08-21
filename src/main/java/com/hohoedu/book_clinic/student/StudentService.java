package com.hohoedu.book_clinic.student;

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
        List<StudentRespDTO.StudentInfoRowDTO> students = studentRepository.findStudentByCenterCode(centerCode);

        for (StudentRespDTO.StudentInfoRowDTO student : students) {
            ClinicRespDTO.MainLevelInfoDTO levelInfo = clinicService.getMainLevelInfo(student.getStudentId());
            student.setLevelNo(levelInfo.getLevelNo());
            student.setLevelTitle(levelInfo.getTitle());
        }

        return students;
    }
}
