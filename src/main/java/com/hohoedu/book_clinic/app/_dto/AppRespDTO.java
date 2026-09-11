package com.hohoedu.book_clinic.app._dto;

import java.util.List;

import lombok.Data;

/**
 * i-with(학부모 Flutter 앱) 화면 응답 모음.
 *
 * app 패키지는 도메인이 아니라 "앱 화면"이 단위라, 내부 클래스 이름에 화면 이름을 접두사로 붙인다
 * (BookstoreMainDTO / BookstoreReportDTO …). 화면이 늘어도 파일은 이 하나다.
 */
public class AppRespDTO {

    /**
     * 책방 메인 화면 응답. 단일 쿼리(AppMapper.xml#selectBookstoreMain) 한 행을 그대로 담는다.
     * 값이 없는 항목은 null (학생이 예약/이용권/독서기록이 없을 수 있음).
     */
    @Data
    public static class BookstoreMainDTO {

        private String studentName;

        // 다음 예약 (ends_at 이 현재 이후인 가장 이른 슬롯)
        private String startDate;    // yyyy-MM-dd
        private String startDayName; // 월요일 ...
        private String startTime;    // HH:mm
        private String endTime;      // HH:mm

        // 직전 이용 (가장 최근 diary, 체크아웃은 diary.out_time → 없으면 해당 예약 종료시간)
        private String lastVisitDate;    // yyyy-MM-dd
        private String lastVisitDayName; // 월요일 ...
        private String checkIn;          // HH:mm
        private String checkOut;         // HH:mm

        // 이용권 (활성 pass: revoked 아님 + 유효기간 내, 가장 최근 부여분)
        private Integer passTotal;
        private Integer passRemain;

        // 최근 독서 기록 이미지 (최신순 4개, 없으면 null)
        private String bookImg1;
        private String bookImg2;
        private String bookImg3;
        private String bookImg4;
    }

    /**
     * 정독 결과(리포트) 화면 응답.
     *
     * 화면 상단의 일자 탭이 조회 단위다 — {@link #dates} 는 최근 정독 일자 4개(오름차순),
     * 나머지 값은 전부 {@link #recordDate} 하루치다. 탭을 누르면 앱이 그 날짜로 다시 호출한다.
     *
     * 누적 성격의 값(totalBookCount, tendencies, monthly)도 "그 날짜까지"로 잘라서 낸다.
     * 과거 탭을 눌렀는데 미래 기록이 섞여 보이면 탭의 의미가 사라지기 때문이다.
     *
     * 일지가 한 건도 없는 학생은 dates 가 비고 나머지는 0/빈 리스트다(화면에서 빈 상태 처리).
     */
    @Data
    public static class BookstoreReportDTO {

        private String studentName;

        /** 상단 탭에 찍을 정독 일자 yyyy-MM-dd, 오름차순 최대 4개. */
        private List<String> dates;

        /** 지금 보고 있는 일자 yyyy-MM-dd. 기록이 없으면 null. */
        private String recordDate;

        // ── 요약 ──
        private int bookCount;        // 그날 읽은 책 수
        private int readMinutes;      // 그날 독서 시간(분) 합
        private Integer correctRate;  // 그날 평균 정답률(%) — 푼 문제가 없으면 null
        private int totalBookCount;   // 그 날짜까지 누적 완독 권수

        /**
         * "이번 정독활동에서는 …" 요약 문장. 생성 정책이 정해지지 않아 항상 null 이다.
         * 앱은 null 이면 문장 영역을 통째로 숨긴다.
         */
        private String summaryText;

        private List<ReportBookDTO> books;
        private List<ReportBadgeDTO> badges;
        private List<ReportTendencyDTO> tendencies;
        private List<ReportMonthlyDTO> monthly;
    }

    /** 정독 결과 — 그날 읽은 책 1권 = 1행. 화면의 책 탭 + 문제 풀이 결과. */
    @Data
    public static class ReportBookDTO {
        private Integer contentId;
        private String title;
        private String imageUrl;

        private int basicCorrect;     // 정독(기본) 최종 정답 수
        private int basicTotal;
        private int advancedCorrect;  // 문해력(심화) 최종 정답 수
        private int advancedTotal;

        /** 기본 "처음 점수" — 재도전 전 최초 제출값. 재도전이 없으면 basicCorrect 와 같다. */
        private Integer firstBasicCorrect;
        private Integer firstBasicTotal;

        /** 기본 문제 재도전 횟수 (제출 회차 - 1). 첫 제출만 했으면 0. */
        private int retryCount;

        /** 정답률(%) — 기본+심화 합산. 푼 문제가 없으면 null. */
        private Integer correctRate;

        /**
         * "문해력이 자랐어요" 낱말들. itempool.ans 는 보기 번호라 낱말을 뽑을 수 없어
         * 항상 null 이다 — 낱말 컬럼이 생기기 전까지 앱은 이 영역을 숨긴다.
         */
        private List<String> growthWords;
    }

    /** 정독 결과 — 보상 4칸. 뱃지 마스터 4종을 항상 전부 내리고 그날 획득분만 earned=true. */
    @Data
    public static class ReportBadgeDTO {
        private Integer badgeId;
        private String badgeName;
        private String badgeDesc;
        private boolean earned;
    }

    /** 정독 결과 — 독서 성향. 문제 유형(erp_bookstore_code gubun='T')별 누적 정답률. */
    @Data
    public static class ReportTendencyDTO {
        private String typeCode;   // '01' ...
        private String typeName;   // 이해 / 표현 / 어휘 ...
        private Double rate;       // 정답률(%)
        private int answerCount;   // 표본 수 — 앱에서 신뢰도 판단용
    }

    /** 정독 결과 — 독서량 그래프 한 점. 앱의 ReportGraphData 와 필드명을 맞춰 문자열로 낸다. */
    @Data
    public static class ReportMonthlyDTO {
        private String year;   // '2026'
        private String month;  // '09'
        private String count;  // '3'
    }
}
