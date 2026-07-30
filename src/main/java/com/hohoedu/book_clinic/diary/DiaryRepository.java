package com.hohoedu.book_clinic.diary;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hohoedu.book_clinic.diary._dto.DiaryRespDTO;

/** 독서일지 조회 MyBatis 매퍼 인터페이스 — 예약 기준 목록 + 일지/추천 기준 도서 목록 (2026-07-30) */
@Mapper
public interface DiaryRepository {

    /** 예약을 루트로 둔 일지 목록 (일지가 있으면 그 값으로 덮어서 내려온다) */
    List<DiaryRespDTO.RowDTO> findDiaryRows(@Param("date") LocalDate date,
                                            @Param("centerCode") String centerCode,
                                            @Param("timeSlot") String timeSlot,
                                            @Param("keyword") String keyword);

    /** 그날 그 센터 학생들이 읽은 책 전체 — 학생별로 묶어 행에 붙인다(DiaryService) */
    List<DiaryRespDTO.BookDTO> findDiaryBooks(@Param("date") LocalDate date,
                                              @Param("centerCode") String centerCode);

    /** 기타 전달사항 저장 — help_needed(모니터링 전용 상태값)는 건드리지 않는다 */
    void upsertDiaryNote(@Param("sessionId") Integer sessionId,
                         @Param("memo") String memo,
                         @Param("createdBy") String createdBy);

    /** 등/하원 시각 보정 — 일지가 없으면 헤더를 만들면서 넣는다("HH:mm" 문자열, 빈 값은 NULL) */
    void upsertDiaryTime(@Param("sessionId") Integer sessionId,
                         @Param("inTime") String inTime,
                         @Param("outTime") String outTime);

    /** 저장 결과 되읽기 — 화면에 되돌려줄 값 */
    DiaryRespDTO.TimeRespDTO findDiaryTime(@Param("sessionId") Integer sessionId);

    /** 세션 소속 센터 — 다른 센터 세션을 건드리지 못하게 막기 위한 확인용(없으면 null) */
    String findSessionCenterCode(@Param("sessionId") Integer sessionId);

}
