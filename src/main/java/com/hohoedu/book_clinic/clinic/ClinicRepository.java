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

    /** 추천된 마스터 도서(contentId)의 실물 재고 중 센터에서 대여 가능한 bcode 1건 선택 (없으면 null) */
    String pickAvailableBcode(@Param("contentId") Integer contentId, @Param("centerCode") String centerCode);

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

    /** 도서의 권장 학년(schoolyear) — EXP 산정 기준 (없으면 null) */
    String findContentSchoolyear(@Param("contentId") Integer contentId);

    /** 학년별 권당 EXP (규칙 없으면 null) */
    Integer findExpPerBook(@Param("schoolyear") String schoolyear);

    /** 레벨 마스터 전체 (레벨업 계산용) */
    List<ClinicRespDTO.LevelDTO> findAllLevels();

    /** 레벨 마스터 1건 상세 (없는 레벨번호면 null — 레벨0 조회 시 방어용) */
    ClinicRespDTO.LevelDetailDTO findLevelDetail(@Param("levelNo") int levelNo);

    /** 학생 누적 EXP/레벨 (student_info 행이 없으면 null — 최초 합격 시 upsert로 생성) */
    ClinicRespDTO.StudentExpDTO findStudentExp(@Param("studentId") String studentId);

    /** EXP 적립 + 레벨 갱신 + 완독 수 증가 (student_info 행이 없으면 새로 생성) */
    void upsertStudentExp(@Param("studentId") String studentId, @Param("expGained") int expGained,
                           @Param("levelNo") int levelNo);

    /** 이번 달(completed_at 기준)에 합격 완료한 도서 목록 — 최신순, 최대 limit건 */
    List<ClinicRespDTO.MonthBookDTO> findCompletedThisMonth(@Param("studentId") String studentId,
                                                             @Param("limit") int limit);

    // ── 뱃지 판정 (2026-07-10) — 카운터를 따로 쌓지 않고 매 제출마다 로그에서 재계산한다 ──

    /** 뱃지 마스터 전체 (badge_id 순) */
    List<ClinicRespDTO.BadgeDTO> findAllBadges();

    /** 학생이 이미 획득한 뱃지 ID 목록 */
    List<Integer> findEarnedBadgeIds(@Param("studentId") String studentId);

    /** 뱃지 획득 기록 — 호출 전 미보유 확인 필수 (PK 중복 시 예외) */
    void insertStudentBadge(@Param("studentId") String studentId, @Param("badgeId") Integer badgeId);

    /** 완독(DONE) 총 권수 — FIRST_BOOK 판정 */
    int countDoneBooks(@Param("studentId") String studentId);

    /** 완독이 1권 이상 있는 달 목록('yyyy-MM', 중복 제거·오름차순) — MONTH_STREAK 판정 재료 */
    List<String> findDoneMonths(@Param("studentId") String studentId);

    /** 독서왕(grade='KING') 달성 횟수 — CROWN 판정 */
    int countKingGrades(@Param("studentId") String studentId);

    /**
     * 문제유형(T코드)별 "만점 책 수" — QTYPE_PERFECT 판정.
     * 완독(DONE) 책마다 합격을 만든 회차(합격선 이상 최초 제출) 하나만 보고, 그 회차에서
     * 해당 유형 문항을 빠짐없이 전부 맞힌 경우만 1회로 센다 (재도전/재제출 어뷰징 차단, 책당 1회)
     */
    List<ClinicRespDTO.QtypePerfectDTO> countQtypePerfectBooks(@Param("studentId") String studentId);

    /** 키워드가 하나라도 포함된(content.keywords LIKE) 완독 책 수 — TOPIC 판정 */
    int countTopicBooks(@Param("studentId") String studentId, @Param("keywords") List<String> keywords);

    /** 심화(qlevel=02) 전 문항 정답 회차가 있는 책 수(책당 1회) — ADV_PERFECT 판정 */
    int countAdvancedPerfectBooks(@Param("studentId") String studentId);

}
