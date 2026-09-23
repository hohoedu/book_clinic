package com.hohoedu.book_clinic.app;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import com.hohoedu.book_clinic.app._dto.AppRespDTO;

import lombok.RequiredArgsConstructor;

/**
 * 정독 결과(리포트) 조립 (2026-09-23).
 *
 * 앱의 정독 결과 화면(POST /app/bookstore/report)과 관리자 발송 미리보기
 * (POST /admin/growth/app-send/report)가 같은 응답을 써야 해서 조립을 여기로 모았다 —
 * 미리보기는 "학부모에게 이렇게 나간다"를 보여주는 화면이라, 조립이 두 벌이 되는 순간
 * 미리보기와 실제 발송물이 조용히 갈라진다.
 *
 * 두 곳의 차이는 sentOnly 하나뿐이다. 앱은 발송된 일지만 보고(true), 미리보기는 발송 전
 * 일지를 봐야 하므로 false 다.
 *
 * 쿼리가 여섯 개로 나뉘는 건 카디널리티가 서로 달라서다(요약 1행 / 책 N행 / 뱃지 5행 /
 * 성향 유형별 / 월별). 여기서는 이어붙이기만 하고 계산은 전부 SQL 이 한다.
 */
@Service
@RequiredArgsConstructor
public class AppReportService {

    private final AppRepository appRepository;

    /**
     * @param recordDate 볼 일자(yyyy-MM-dd). null 이면 가장 최근 정독 일자를 고른다.
     * @param sentOnly   true 면 발송된 일지(is_send=1)만 대상으로 한다.
     *
     * 기록이 한 건도 없으면 빈 리포트(dates=[], recordDate=null)를 그대로 낸다 —
     * 기록이 없는 건 오류가 아니라 화면의 빈 상태이고, 호출부가 에러 분기와 빈 상태 분기를
     * 둘 다 들고 있을 이유가 없다.
     */
    public AppRespDTO.BookstoreReportDTO getReport(String studentId, String recordDate, boolean sentOnly) {
        List<String> dates = appRepository.selectBookstoreReportDates(studentId, sentOnly);
        Collections.reverse(dates); // 화면의 일자 탭은 과거 → 현재 순서다

        String requested = trimToNull(recordDate);
        String targetDate = requested != null ? requested
                : (dates.isEmpty() ? null : dates.get(dates.size() - 1));

        AppRespDTO.BookstoreReportDTO res = appRepository.selectBookstoreReportSummary(studentId, targetDate, sentOnly);
        if (res == null) res = new AppRespDTO.BookstoreReportDTO();
        res.setDates(dates);
        res.setRecordDate(targetDate);

        if (targetDate == null) {
            res.setBooks(Collections.emptyList());
            res.setBadges(Collections.emptyList());
            res.setTendencies(Collections.emptyList());
            res.setMonthly(Collections.emptyList());
            return res;
        }

        res.setBooks(appRepository.selectBookstoreReportBooks(studentId, targetDate, sentOnly));
        res.setBadges(appRepository.selectBookstoreReportBadges(studentId, targetDate, sentOnly));
        res.setTendencies(appRepository.selectBookstoreReportTendencies(studentId, targetDate, sentOnly));

        // 월별 독서량은 완독 이력(recommend_log) 기준이지만 발송 여부는 똑같이 탄다 —
        // 결과가 아직 안 보이는데 그래프만 먼저 오르면 두 화면이 어긋난다(2026-09-23).
        // 쿼리가 그 해 1월부터 오름차순으로 주므로 여기서 뒤집지 않는다.
        res.setMonthly(appRepository.selectBookstoreReportMonthly(studentId, targetDate, sentOnly));

        return res;
    }

    private static String trimToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
