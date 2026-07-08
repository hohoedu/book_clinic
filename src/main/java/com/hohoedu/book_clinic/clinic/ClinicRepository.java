package com.hohoedu.book_clinic.clinic;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hohoedu.book_clinic.clinic._dto.ClinicRespDTO;

/**
 * 학생 독서 클리닉(student-main 화면) MyBatis 매퍼 인터페이스
 */
@Mapper
public interface ClinicRepository {

    /** 학생 이름 + 독서 현황 + 레벨/캐릭터 조회 (student_info가 없으면 null) */
    ClinicRespDTO.StudentInfoDTO findStudentInfo(@Param("studentId") String studentId);

    /** 레벨 마스터 전체 (진행률/남은 권수 계산용) */
    List<ClinicRespDTO.LevelDTO> findAllLevels();

    /** 학년별 권당 EXP (규칙 없으면 null) */
    Integer findExpPerBook(@Param("schoolyear") String schoolyear);

    /** 획득 뱃지 목록 (최근 획득 순) */
    List<ClinicRespDTO.BadgeDTO> findBadges(@Param("studentId") String studentId);

    /** 이번 달에 읽은 책 (완독은 이번 달 완독 건만, 읽는 중은 항상 포함 / 최근순) */
    List<ClinicRespDTO.MonthBookDTO> findMonthBooks(@Param("studentId") String studentId);

    /** 오늘 이미 추천된 도서가 있으면 그 도서 카드 조회 (없으면 null) */
    ClinicRespDTO.RecommendBookDTO findTodayRecommend(@Param("studentId") String studentId);

    /** 가장 최근 완독(문제풀이 완료) 도서의 분류/장르 (없으면 null) */
    ClinicRespDTO.LastReadDTO findLastRead(@Param("studentId") String studentId);

    /** 학생 소속 센터 코드 */
    String findCenterCode(@Param("studentId") String studentId);

    /** 학생 학년 코드 (erp_student.grade_key, S코드 01~07) — 없으면 null */
    String findGradeKey(@Param("studentId") String studentId);

    /**
     * 우선순위 순으로 조건(문제풀이 미완료 · 소속 센터 대여 가능 · 직전 완독과 분류/장르 다름)을
     * 모두 만족하는 첫 도서를 단일 쿼리로 선택 (없으면 null)
     */
    Integer pickNextContentId(@Param("studentId") String studentId, @Param("centerCode") String centerCode,
                               @Param("year") String year, @Param("schoolyear") String schoolyear,
                               @Param("lastType") String lastType, @Param("lastGenre") String lastGenre);

    /** 추천 도서 카드 상세 조회 */
    ClinicRespDTO.RecommendBookDTO findBookCard(@Param("contentId") Integer contentId);

    /** 추천 이력 기록 */
    void insertRecommendLog(@Param("studentId") String studentId, @Param("contentId") Integer contentId);

    /** 추천된 마스터 도서(contentId)의 실물 재고 중 센터에서 대여 가능한 bcode 1건 선택 (없으면 null) */
    String pickAvailableBcode(@Param("contentId") Integer contentId, @Param("centerCode") String centerCode);
}
