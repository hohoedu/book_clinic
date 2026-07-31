package com.hohoedu.book_clinic.monitor;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hohoedu.book_clinic.monitor._dto.MonitorRespDTO;

/** 실시간 모니터링(클리닉 입실/퇴실/독서일지) MyBatis 매퍼 인터페이스 (2026-07-15) */
@Mapper
public interface MonitorRepository {

    /** 오늘 그 학생의 열린(ENTERED) 세션 ID — 없으면 null */
    Integer findOpenSessionId(@Param("studentId") String studentId, @Param("date") LocalDate date);

    /** 신규 입실 세션 생성 */
    void insertSession(@Param("studentId") String studentId, @Param("date") LocalDate date);

    /** 퇴실 처리 (status=EXITED, exited_at=now) */
    void updateSessionExit(@Param("sessionId") Integer sessionId);

    /** 문제풀이 화면 진입 시각 기록 — "문제 푸는 중" 카드 상태의 기준 */
    void markQuizStarted(@Param("sessionId") Integer sessionId);

    /** 채점 제출 시 문제풀이 진행 상태 해제 */
    void clearQuizStarted(@Param("sessionId") Integer sessionId);

    /** 결과 화면 진입 시각 기록 — "결과 확인중" 카드 상태의 기준 */
    void markResultViewing(@Param("sessionId") Integer sessionId);

    /** 결과 화면 이탈(홈으로 등) 시 결과 확인 상태 해제 */
    void clearResultViewing(@Param("sessionId") Integer sessionId);

    /** 특정 날짜·센터의 예약 기준 전체 카드 목록 (예약 없이 입실한 세션은 포함되지 않음) */
    List<MonitorRespDTO.CardDTO> findReservationCards(@Param("date") LocalDate date, @Param("centerCode") String centerCode);

    /** 사용 중(use_yn=1)인 독서태도 코드 목록 — 독서일지 패널 체크박스 렌더링용 */
    List<MonitorRespDTO.AttitudeCodeDTO> findActiveAttitudeCodes();

    /** 카드 캐러셀용 — 그 학생이 오늘 추천받은 책 전체(+ 날짜 상관없이 아직 PENDING인 책) */
    List<MonitorRespDTO.BookPageDTO> findTodayBooks(@Param("studentId") String studentId, @Param("date") LocalDate date);

    /** 세션 1건의 카드 상세 — Firestore 동기화/저장 직후 최신값 재조회용 */
    MonitorRespDTO.CardDTO findCardBySessionId(@Param("sessionId") Integer sessionId);

    /** 독서일지 upsert (세션당 1건) */
    /** 독서일지 헤더 upsert — record_date/record_time/in_time은 세션·예약에서 끌어와 최초 1회만 채운다 */
    void upsertDiary(@Param("sessionId") Integer sessionId, @Param("helpNeeded") Boolean helpNeeded,
                     @Param("memo") String memo, @Param("createdBy") String createdBy);

    /**
     * 도움 필요 상태 갱신 — 하루치 기록이 아니라 풀릴 때까지 유지되는 학생 상태값(erp_student.help_needed).
     * 일지의 help_needed(그날 스냅샷)와 짝으로 함께 저장한다(MonitorService.saveDiary).
     */
    void updateStudentHelpNeeded(@Param("studentId") String studentId, @Param("helpNeeded") Boolean helpNeeded);

    Integer findDiaryKeyBySessionId(@Param("sessionId") Integer sessionId);

    void deleteDiaryAttitudes(@Param("diaryKey") Integer diaryKey);

    void insertDiaryAttitudes(@Param("diaryKey") Integer diaryKey, @Param("studentId") String studentId,
                              @Param("attitudeCodes") java.util.List<String> attitudeCodes);

    /** 일지 헤더가 없으면 세션 정보로 생성만 한다(기존 행은 손대지 않음) */
    void ensureDiary(@Param("sessionId") Integer sessionId);

    /** 퇴실 시점에 diary.out_time을 세션 exited_at으로 채운다(이미 값이 있으면 보존) */
    void syncDiaryOutTime(@Param("sessionId") Integer sessionId);

    // ── 문제풀이 기록 삭제(초기화) — MonitorService.resetQuiz (2026-07-31) ──────────────

    /** 삭제 대상 추천 1건 + 초기화 직전 스냅샷 — 없거나 다른 학생 것이면 null */
    MonitorRespDTO.QuizResetTargetDTO findQuizResetTarget(@Param("recommendId") Integer recommendId,
                                                          @Param("studentId") String studentId);

    /**
     * 삭제 대상보다 뒤에 추천받은 책들 — 학생이 그 책 문제로 되돌아가려면 이 책들이 비켜줘야 한다.
     * 오래된 순으로 반환한다.
     */
    List<MonitorRespDTO.QuizResetTargetDTO> findLaterRecommends(@Param("studentId") String studentId,
                                                                @Param("recommendId") Integer recommendId);

    /** 추천 이력 행 자체를 삭제 — 뒤 추천(CANCEL) 전용. 참조하는 자식 행을 모두 지운 뒤 호출해야 한다 */
    void deleteRecommendLog(@Param("recommendId") Integer recommendId);

    /** 그 추천의 문제풀이 이력(기본+심화 전 회차) 삭제 — 반환값은 지운 행 수 */
    int deleteQuizAnswerLogs(@Param("recommendId") Integer recommendId);

    /** 그 책에서 딴 뱃지 회수 — 안 지우면 재도전이 "첫 시도"로 안 잡혀 등급이 고정된 채로 남는다 */
    int deleteStudentBadges(@Param("studentId") String studentId, @Param("contentId") Integer contentId);

    /** 그 책의 NORMAL 카드 회수 */
    int deleteNormalCard(@Param("studentId") String studentId, @Param("contentId") Integer contentId);

    /** NORMAL 카드가 줄어 근거가 사라진 RARE 카드(trigger_count > 현재 보유 수) 회수 */
    int deleteOrphanRareCards(@Param("studentId") String studentId);

    /** 그 추천으로 적재된 일지 상세 삭제 — 점수/독서시간 스냅샷이 통째로 사라진다 */
    int deleteDiaryDetailByRecommend(@Param("recommendId") Integer recommendId);

    /** 되돌린 책을 같은 책의 다른 사본으로 확보했을 때, 추천이 가리키는 실물 판본을 맞춰준다 */
    void updateRecommendItem(@Param("recommendId") Integer recommendId, @Param("itemId") Integer itemId);

    /** 추천 이력을 문제 풀기 전 상태(PENDING·정답수 null)로 되돌린다 — 행 자체는 남긴다 */
    void resetRecommendResult(@Param("recommendId") Integer recommendId);

    /** 삭제 이력 적재 (erp_bookstore_quiz_reset_log) — logType은 RESET(대상 책) / CANCEL(뒤 추천) */
    void insertQuizResetLog(@Param("target") MonitorRespDTO.QuizResetTargetDTO target,
                            @Param("logType") String logType,
                            @Param("loggedBy") String loggedBy,
                            @Param("answerRows") int answerRows,
                            @Param("badgeRows") int badgeRows,
                            @Param("cardRows") int cardRows);

    /** 채점 결과를 일지 상세에 적재 — 같은 (일지, 도서)면 제출한 난이도 컬럼만 갱신 */
    void upsertDiaryDetail(@Param("diaryKey") Integer diaryKey, @Param("contentId") Integer contentId,
                           @Param("recommendId") Integer recommendId, @Param("qlevel") String qlevel,
                           @Param("correctCount") Integer correctCount, @Param("totalCount") Integer totalCount);

}
