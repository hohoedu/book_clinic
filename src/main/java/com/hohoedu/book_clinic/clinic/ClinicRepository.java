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

    /** 활성 순위 초안의 추천 후보 목록 (순위 순) */
    List<ClinicRespDTO.CandidateDTO> findCandidates(@Param("year") String year, @Param("schoolyear") String schoolyear);

    /** 학생이 읽은(읽는 중 포함) 도서 content_id 목록 */
    List<Integer> findReadContentIds(@Param("studentId") String studentId);

    /** 학생에게 이미 추천됐던 도서 content_id 목록 */
    List<Integer> findRecommendedContentIds(@Param("studentId") String studentId);

    /** 가장 최근 완독 도서의 분류/장르 (없으면 null) */
    ClinicRespDTO.LastReadDTO findLastRead(@Param("studentId") String studentId);

    /** 추천 도서 카드 상세 조회 */
    ClinicRespDTO.RecommendBookDTO findBookCard(@Param("contentId") Integer contentId);

    /** 추천 이력 기록 */
    void insertRecommendLog(@Param("studentId") String studentId, @Param("contentId") Integer contentId);
}
