package com.hohoedu.book_clinic.clinic;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hohoedu.book_clinic.clinic._dto.ClinicReqDTO;
import com.hohoedu.book_clinic.clinic._dto.ClinicRespDTO;

/**
 * 학생 독서 클리닉(student-main 화면) MyBatis 매퍼 인터페이스
 * 1단계(책 추천) → 2단계(문제풀이/채점) 재설계 (2026-07-09)
 */
@Mapper
public interface ClinicRepository {

    /** 학생 소속 센터 코드 */
    String findCenterCode(@Param("studentId") String studentId);

    /** 학생 학년 코드 (erp_student.grade_key, S코드 01~07) — 없으면 null */
    String findGradeKey(@Param("studentId") String studentId);

    /** 직전 추천 도서의 분류/장르 (추천 이력이 없으면 null) */
    ClinicRespDTO.LastRecommendDTO findLastRecommend(@Param("studentId") String studentId);

    /**
     * 우선순위 순으로 조건을 모두 만족하는 첫 도서 1건 선택 (없으면 null)
     *   - 이 학생에게 아직 추천된 적 없을 것
     *   - 학생 소속 센터에 대여 가능한 실물 재고가 있을 것
     *   - applyDedup=true일 때만: 직전 추천 도서와 분류·장르가 모두 같으면 제외
     */
    Integer pickNextContentId(@Param("studentId") String studentId, @Param("centerCode") String centerCode,
                               @Param("year") String year, @Param("schoolyear") String schoolyear,
                               @Param("lastType") String lastType, @Param("lastGenre") String lastGenre,
                               @Param("applyDedup") boolean applyDedup);

    /** 추천 도서 카드 상세 조회 */
    ClinicRespDTO.RecommendBookDTO findBookCard(@Param("contentId") Integer contentId);

    /** 이 학생이 아직 풀지 않은(PENDING) 추천 도서 카드 — 있으면 재로그인해도 같은 책을 그대로 보여준다 */
    ClinicRespDTO.RecommendBookDTO findPendingRecommendBookCard(@Param("studentId") String studentId);

    /** 추천 이력 기록 (신규 추천 시 status='PENDING'으로 생성됨) */
    void insertRecommendLog(@Param("studentId") String studentId, @Param("contentId") Integer contentId);

    /** 학생+도서의 추천 기록 ID와 현재 상태 (없으면 null) */
    ClinicRespDTO.RecommendLogStatusDTO findRecommendLogStatus(@Param("studentId") String studentId,
                                                                @Param("contentId") Integer contentId);

    /** 문제 풀이 이력 저장 — 제출 1회분의 문항별 선택 답안을 한 번에 적재 (재도전 제출도 모두 남긴다) */
    void insertQuizAnswerLogs(@Param("recommendId") Integer recommendId,
                               @Param("studentId") String studentId,
                               @Param("contentId") Integer contentId,
                               @Param("qlevel") String qlevel,
                               @Param("answerLogs") List<ClinicReqDTO.AnswerLogDTO> answerLogs);

    /** 기본 문제풀이 채점 결과 반영 (합격 시 status=DONE, 미달이면 PENDING 유지) */
    void updateRecommendResult(@Param("recommendId") Integer recommendId,
                                @Param("correctCount") Integer correctCount,
                                @Param("totalCount") Integer totalCount,
                                @Param("grade") String grade,
                                @Param("status") String status);

    /** 학년(단계)별 레벨업 1회당 필요 완독 권수 (규칙 없으면 null) */
    Integer findBooksPerLevel(@Param("schoolyear") String schoolyear);

    /** 특정 학년 도서의 완독(DONE) 권수 — 레벨 계산 기준 (단계 = 학생 학년) */
    int countDoneBooksByGrade(@Param("studentId") String studentId, @Param("schoolyear") String schoolyear);

    /** 단계(학년)+레벨의 단계명/칭호/특징/필요권수 (학년 규칙이 없으면 null, 칭호 미시딩이면 title만 null) */
    ClinicRespDTO.LevelDetailDTO findLevelDetail(@Param("schoolyear") String schoolyear,
                                                  @Param("levelNo") int levelNo);

    /** 보유 카드 = 완독(DONE)한 책 전체 (책당 1장), 최신 완독순 — 카드 컬렉션 패널용 */
    List<ClinicRespDTO.CardDTO> findEarnedCards(@Param("studentId") String studentId);

    /** 특정 책의 카드 정보(제목/저자/표지) — 신규 완독 시 결과화면 팝업용 (없으면 null) */
    ClinicRespDTO.CardDTO findCardByContent(@Param("contentId") Integer contentId);

    /** 이번 달(completed_at 기준)에 합격 완료한 도서 목록 — 최신순, 최대 limit건 */
    List<ClinicRespDTO.MonthBookDTO> findCompletedThisMonth(@Param("studentId") String studentId,
                                                             @Param("limit") int limit);

    // ── 뱃지 판정 (2026-07-27 재작업) — 책마다 부여 · 등급형 배타 · 첫 시도 결과 기준 ──

    /** 뱃지 마스터 1건 (badge_id로 이름/설명 조회 — 결과화면 팝업용) */
    ClinicRespDTO.BadgeDTO findBadge(@Param("badgeId") Integer badgeId);

    /** 학생이 획득한 뱃지 상세 목록(책마다 부여, 이름/설명 포함) — 획득 최신순 */
    List<ClinicRespDTO.BadgeDTO> findEarnedBadges(@Param("studentId") String studentId);

    /** 뱃지 획득 기록 — 책(content)마다 (student, content, badge) 단위. 첫 시도에서 1회만 적재 */
    void insertStudentBadge(@Param("studentId") String studentId,
                            @Param("contentId") Integer contentId,
                            @Param("badgeId") Integer badgeId);

    /** 해당 책+난이도(qlevel)의 기존 제출 회차 수 — 0이면 이번이 첫 시도(뱃지 등급 판정 기준) */
    int countPriorAttempts(@Param("studentId") String studentId,
                           @Param("contentId") Integer contentId,
                           @Param("qlevel") String qlevel);

    /** 완독(DONE) 총 권수 — 카드 총 수 계산용 */
    int countDoneBooks(@Param("studentId") String studentId);

}
