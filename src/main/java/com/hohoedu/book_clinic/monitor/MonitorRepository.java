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

    /** 카드 캐러셀용 — 그 학생이 오늘 추천받은 책 전체(+ 날짜 상관없이 아직 PENDING인 책) */
    List<MonitorRespDTO.BookPageDTO> findTodayBooks(@Param("studentId") String studentId, @Param("date") LocalDate date);

    /** 세션 1건의 카드 상세 — Firestore 동기화/저장 직후 최신값 재조회용 */
    MonitorRespDTO.CardDTO findCardBySessionId(@Param("sessionId") Integer sessionId);

    /** 독서일지 upsert (세션당 1건) */
    void upsertReadingLog(@Param("sessionId") Integer sessionId, @Param("studentId") String studentId,
                           @Param("attitudeCodes") String attitudeCodes, @Param("helpNeeded") String helpNeeded,
                           @Param("note") String note, @Param("createdBy") String createdBy);

}
