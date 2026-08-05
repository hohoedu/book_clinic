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

    /** 이 학생이 속한 형제 그룹 전체(본인 포함) — 결제창 형제 선택 화면용. 형제가 없으면 본인 1건만 돌아온다 */
    List<Student> findSiblingGroup(@Param("studentId") String studentId);
}
