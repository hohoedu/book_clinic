package com.hohoedu.book_clinic.reservation;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic.reservation._dto.ReservationRespDTO;
import com.hohoedu.book_clinic.student.StudentRepository;
import com.hohoedu.book_clinic.student.model.Student;

import lombok.RequiredArgsConstructor;

/** 클리닉 예약 등록/삭제/조회 — 실시간 모니터링(2026-07-15)이 읽는 예약 마스터를 관리 (2026-07-23) */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final StudentRepository studentRepository;

    /** 같은 (studentId, date, timeSlot) 예약이 이미 있으면 등록을 막는다(중복 예약 방지) */
    @Transactional
    public void register(String studentId, LocalDate date, String timeSlot) {
        if (reservationRepository.existsReservation(studentId, date, timeSlot)) {
            throw new Exception400("이미 등록된 예약입니다.");
        }
        reservationRepository.insertReservation(studentId, date, timeSlot);
    }

    public void delete(Integer reservationId) {
        reservationRepository.deleteReservation(reservationId);
    }

    public List<ReservationRespDTO.ReservationRowDTO> findByDate(LocalDate date) {
        return reservationRepository.findByDate(date);
    }

    /** 이름/appId로 학생 검색 — 예약 등록 화면의 학생 선택용 */
    public List<ReservationRespDTO.StudentSearchDTO> searchStudents(String keyword) {
        return studentRepository.searchByKeyword(keyword).stream()
                .map(this::toStudentSearchDTO)
                .toList();
    }

    private ReservationRespDTO.StudentSearchDTO toStudentSearchDTO(Student student) {
        ReservationRespDTO.StudentSearchDTO dto = new ReservationRespDTO.StudentSearchDTO();
        dto.setStudentId(student.getStudentId());
        dto.setStudentName(student.getStudentName());
        dto.setSchool(student.getSchool());
        dto.setGradeKey(student.getGradeKey());
        dto.setAppId(student.getAppId());
        return dto;
    }

}
