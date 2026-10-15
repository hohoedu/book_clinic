package com.hohoedu.book_clinic.app;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import org.springframework.stereotype.Service;

import com.hohoedu.book_clinic.app._dto.AppRespDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppReportService {

    private final AppRepository appRepository;

    public AppRespDTO.BookstoreReportDTO getReport(String studentId, String recordDate, boolean sentOnly) {
        List<String> dates = appRepository.selectBookstoreReportDates(studentId, sentOnly);
        Collections.reverse(dates); // 화면의 일자 탭은 과거 → 현재 순서다

        String requested = trimToNull(recordDate);
        String targetDate = requested != null ? requested
                : (dates.isEmpty() ? null : dates.get(dates.size() - 1));

        AppRespDTO.BookstoreReportDTO res = appRepository.selectBookstoreReportSummary(studentId, targetDate, sentOnly);
        if (res == null)
            res = new AppRespDTO.BookstoreReportDTO();
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
        List<AppRespDTO.ReportTendencyDTO> tendencies = appRepository.selectBookstoreReportTendencies(studentId,
                targetDate, sentOnly);
        res.setTendencies(tendencies);
        fillSummary(res, tendencies, studentId, targetDate);

        // 월별 독서량은 완독 이력(recommend_log) 기준이지만 발송 여부는 똑같이 탄다 —
        // 결과가 아직 안 보이는데 그래프만 먼저 오르면 두 화면이 어긋난다(2026-09-23).
        // 쿼리가 그 해 1월부터 오름차순으로 주므로 여기서 뒤집지 않는다.
        res.setMonthly(appRepository.selectBookstoreReportMonthly(studentId, targetDate, sentOnly));

        return res;
    }

    /**
     * 요약 문장 재료(역량 문구 / 영역명)를 따로 채운다 — 문장 조합은 앱이 한다.
     * 그날 성향 중 보정 점수 최고 영역(동점이면 무작위)의 문구(무작위)를 쓰고, 성향이 없으면 둘 다 null.
     *
     * 영역은 rate 가 아니라 라플라스 보정 (맞힌 수 + 1) / (푼 수 + 2) 로 고른다 — rate 로 고르면
     * 1문제 다 맞힌 영역(100%)이 10문제 중 9.5개 맞힌 영역(95%)을 이긴다. 보정하면 0.67 대 0.88.
     *
     * 무작위는 (학생, 날짜) 시드로 고정한다 — 요청마다 뽑으면 탭을 다시 누를 때마다 문장이 바뀌고,
     * 발송 미리보기에서 본 문장과 학부모가 앱에서 보는 문장이 달라진다.
     */
    private void fillSummary(AppRespDTO.BookstoreReportDTO res, List<AppRespDTO.ReportTendencyDTO> tendencies,
            String studentId, String recordDate) {
        List<AppRespDTO.ReportTendencyDTO> scored = tendencies.stream()
                .filter(t -> t.getAnswerCount() > 0)
                .toList();
        if (scored.isEmpty())
            return;

        // 분모가 모두 정수라 같은 비율이면 double 도 정확히 같은 값이 나온다(동점 판정 안전)
        double max = scored.stream().mapToDouble(AppReportService::smoothedScore).max().getAsDouble();
        List<AppRespDTO.ReportTendencyDTO> top = scored.stream()
                .filter(t -> smoothedScore(t) == max)
                .toList();
        Random random = new Random(Objects.hash(studentId, recordDate));
        AppRespDTO.ReportTendencyDTO pick = top.get(random.nextInt(top.size()));

        List<String> contents = appRepository.selectCompetencyContents(pick.getTypeCode());
        String content = contents.isEmpty() ? null : trimToNull(contents.get(random.nextInt(contents.size())));
        res.setSummaryTypeName(pick.getTypeName());
        if (content == null)
            return;
        res.setSummaryContent(content);
    }

    private static double smoothedScore(AppRespDTO.ReportTendencyDTO t) {
        return (t.getCorrectCount() + 1.0) / (t.getAnswerCount() + 2.0);
    }

    private static String trimToNull(String v) {
        if (v == null)
            return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
