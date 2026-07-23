package com.hohoedu.book_clinic.student;

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

    /** 이름 또는 appId로 학생 검색 (예약 등록 화면의 학생 선택용, 최대 20건) */
    List<Student> searchByKeyword(@Param("keyword") String keyword);
}
