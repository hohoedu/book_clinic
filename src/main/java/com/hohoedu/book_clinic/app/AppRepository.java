package com.hohoedu.book_clinic.app;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hohoedu.book_clinic.app._dto.AppRespDTO;

/**
 * i-with 앱 전용 조회. 화면 하나 = 쿼리 하나. 로직은 담지 않는다.
 * (도메인 서비스가 아니라 화면 조립용 read 이므로 app 패키지에 둔다.)
 *
 * 정독 결과 화면만 예외로 쿼리가 여러 개다 — 일자 탭/책 목록/뱃지/성향/월별이 카디널리티가
 * 전부 달라서 한 행으로 못 합친다. 대신 조립은 컨트롤러가 그대로 이어붙이기만 한다.
 */
@Mapper
public interface AppRepository {

    AppRespDTO.BookstoreMainDTO selectBookstoreMain(@Param("studentId") String studentId);

    // ── 정독 결과 화면 ──

    /** 상단 탭용 최근 정독 일자 (최신순 4개 — 컨트롤러가 뒤집어 오름차순으로 낸다). */
    List<String> selectBookstoreReportDates(@Param("studentId") String studentId);

    /** 요약 한 행 (학생명/그날 권수·독서시간·정답률/누적 권수). 학생이 있으면 항상 1행. */
    AppRespDTO.BookstoreReportDTO selectBookstoreReportSummary(
            @Param("studentId") String studentId, @Param("recordDate") String recordDate);

    List<AppRespDTO.ReportBookDTO> selectBookstoreReportBooks(
            @Param("studentId") String studentId, @Param("recordDate") String recordDate);

    /** 뱃지 마스터 4종 전부 + 그날 획득 여부. */
    List<AppRespDTO.ReportBadgeDTO> selectBookstoreReportBadges(
            @Param("studentId") String studentId, @Param("recordDate") String recordDate);

    /** 문제 유형별 누적 정답률 (그 날짜까지). */
    List<AppRespDTO.ReportTendencyDTO> selectBookstoreReportTendencies(
            @Param("studentId") String studentId, @Param("recordDate") String recordDate);

    /** 월별 완독 권수 (그 날짜까지 최근 6개월, 최신순 — 컨트롤러가 뒤집는다). */
    List<AppRespDTO.ReportMonthlyDTO> selectBookstoreReportMonthly(
            @Param("studentId") String studentId, @Param("recordDate") String recordDate);
}
