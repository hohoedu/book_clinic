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

        // 이용권 (이용 중인 1장 — 다음 예약이 차감될 이용권, PassMapper.findUsablePassOn 과 같은 순서)
        private Integer passTotal;
        private Integer passRemain;
        private String passValidUntil; // yyyy-MM-dd — 사용기한

        // 최근 독서 기록 이미지 (최신순 4개, 없으면 null)
        private String bookImg1;
        private String bookImg2;
        private String bookImg3;
        private String bookImg4;
    }

    /**
     * 보유 이용권 목록의 한 줄 = 결제 1건. 단일 쿼리(AppMapper.xml#selectPassList) 한 행이다.
     *
     * 메인 화면({@link BookstoreMainDTO})의 passRemain 은 이용 중인 한 장의 잔여라
     * 건별 환불을 고를 단위가 되지 못한다. 이용권은 결제 건마다 따로 발급·회수되므로
     * (erp_bookstore_pass 1행 = 결제 1건) 이 화면은 건별로 내려준다.
     *
     * remainCount 는 집계가 아니라 이용권 행의 remain_count 그대로다 — 예약 시 차감/취소 시 복구가
     * 그 컬럼을 조건부 UPDATE 로 관리하고 있어(2026-09-14), 차감 이력을 여기서 다시 세면 취소된
     * 행을 빼는 규칙이 두 곳으로 갈라진다.
     *
     * validUntil 은 아직 첫 예약이 없는 이용권이면 null 이다 — 만료일은 첫 예약 때 정해진다
     * (PassService.consumeForReservation). 화면은 "사용기한 미정"으로 보여주면 된다.
     */
    @Data
    public static class PassListDTO {
        private Integer passId;
        private Integer paymentId;
        private String productName;
        private Integer totalCount;
        private Integer remainCount;
        /** 이 건의 결제 금액 */
        private Integer amount;
        /**
         * 남은 횟수만큼의 비례 환불 금액 = amount / totalCount * remainCount (원 단위 버림).
         * 화면에 "환불하면 대략 얼마"를 보여주는 값이다. 실제 환불액은 환불 규정(경과일수·사용
         * 횟수별 환불율)이 적용되므로 확정 금액은 /payment/refund/quote 가 낸다.
         */
        private Integer refundableAmount;
        private String paidAt;      // yyyy-MM-dd HH:mm
        /** 이용권 등록일. PG 결제는 승인 직후 발급되므로 결제일과 사실상 같다 */
        private String registeredAt; // yyyy-MM-dd HH:mm
        private String validUntil;   // yyyy-MM-dd — 사용기한, 미정이면 null
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

        // "처음 점수"(firstBasicCorrect/firstBasicTotal)는 2026-09-21에 제거했다 — 재제출 결과가 곧
        // 그 학생의 점수가 되면서 basicCorrect와 항상 같은 값이 됐고, 지난 점수는 노출하지 않는다.

        /**
         * 기본 문제 재도전 횟수 (재도전 회차 - 1). 첫 제출만 했으면 0.
         * "틀린 문제 다시 풀기"는 점수에는 반영돼도 이 값을 올리지 않는다(2026-09-21).
         */
        private int retryCount;

        /** 정답률(%) — 기본+심화 합산. 푼 문제가 없으면 null. */
        private Integer correctRate;

        /**
         * "문해력이 자랐어요" 낱말들. itempool.ans 는 보기 번호라 낱말을 뽑을 수 없어
         * 항상 null 이다 — 낱말 컬럼이 생기기 전까지 앱은 이 영역을 숨긴다.
         */
        private List<String> growthWords;
    }

    /** 정독 결과 — 보상 칸. 뱃지 마스터 5종을 항상 전부 내리고 그날 획득분만 earned=true. */
    @Data
    public static class ReportBadgeDTO {
        private Integer badgeId;

        /**
         * BASIC_FAIL / BASIC_PASS / BASIC_PERFECT / ADV_PASS / ADV_PERFECT.
         *
         * 앱이 뱃지 아이콘을 고르는 키다. badge_id 는 4종↔5종 재편으로 두 번 재번호된 전력이 있어
         * (patch-erp_bookstore_badge-*.sql) 이미지 매핑에 쓰면 개편 때마다 그림이 어긋난다.
         * category 는 그 두 번 모두 그대로였다.
         */
        private String category;

        private String badgeName;
        private String badgeDesc;
        private boolean earned;

        /** 그날 이 뱃지를 획득한 횟수(책 단위). 못 받았으면 0 — 화면의 "N번 달성했어요". */
        private int earnedCount;
    }

    /** 정독 결과 — 독서 성향. 문제 유형(erp_bookstore_code gubun='T')별 누적 정답률. */
    @Data
    public static class ReportTendencyDTO {
        private String typeCode;   // '01' ...
        private String typeName;   // 이해 / 표현 / 어휘 ...
        private Double rate;       // 점수(%) — 기본 50% + 맞힌 비율만큼의 나머지 50%
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
