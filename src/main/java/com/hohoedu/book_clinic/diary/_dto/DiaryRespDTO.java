package com.hohoedu.book_clinic.diary._dto;

import java.util.List;

import com.hohoedu.book_clinic.monitor._dto.MonitorRespDTO;

import lombok.Data;

/** 독서일지 화면 응답 DTO 모음 (2026-07-30) */
public class DiaryRespDTO {

    /**
     * 일지 목록 1행 — 루트는 예약(erp_bookstore_reservation + slot_instance)이고, 세션/일지는 있으면 붙는다.
     * 시각·교시는 일지 값이 있으면 그쪽으로(직원 보정값 우선), 없으면 예약/세션 값으로 채워진다.
     */
    @Data
    public static class RowDTO {
        private Integer reservationId;
        private String studentId;
        private String studentName;
        private String gradeKey;
        private String gradeName;      // erp_bookstore_code(gubun='S') 코드명 — "초1" 등
        private String timeSlot;       // 일지 record_time 우선, 없으면 예약 time_slot

        private Integer sessionId;     // null이면 아직 미입실(예약만 있는 상태)
        private String sessionStatus;  // ENTERED / EXITED (raw, 미입실이면 null)
        private Integer diaryKey;      // null이면 일지 미생성 — 화면은 예약 기준 값만 표시

        private String inTime;         // "HH:mm" — 일지 in_time 우선, 없으면 세션 entered_at
        private String outTime;        // "HH:mm" — 일지 out_time 우선, 없으면 세션 exited_at

        private Boolean helpNeeded;
        private String memo;
        private Boolean isSend;
        // 전송 버튼 아이콘 판별용 — 학생 앱 미가입(app_token 없음/빈 값)이면 발송 자체가 불가하다.
        // 토큰 원본은 화면에서 쓸 일이 없어 있음/없음만 내려준다(SQL이 IS NOT NULL/빈 문자열까지 판정).
        private Boolean hasAppToken;

        // 태도 체크는 SQL에서 콤마 문자열로 묶어 받고(attitudeCodesRaw), 화면이 쓰기 쉬운 배열로
        // 쪼개서 attitudeCodes에 담는다(DiaryService).
        private String attitudeCodesRaw;
        private List<String> attitudeCodes;

        /** NOT_ENTERED(미입실) / ENTERED(입실중) / EXITED(퇴실) — sessionStatus에서 파생 */
        private String attendStatus;

        private List<BookDTO> books;
    }

    /**
     * 그날 읽은 책 1권. 일지 상세(diary_detail)에 이미 적재된 책은 그 스냅샷을 쓰고,
     * 아직 제출 전이라 상세가 없는 책은 추천 이력(recommend_log)에서 끌어온다(fromDiary=false).
     */
    @Data
    public static class BookDTO {
        private String studentId;   // 행에 붙이기 위한 그룹 키
        private Integer contentId;
        private String bookTitle;
        private String imageUrl;
        private Integer readMinutes;
        private Integer basicCorrectCount;
        private Integer basicTotalCount;
        private Integer advancedCorrectCount;
        private Integer advancedTotalCount;
        private Boolean fromDiary;  // true면 일지 상세 스냅샷, false면 진행 중(추천 이력) 값
        private Integer sortKey;    // 정렬용(diary_detail.id / recommend_id) — 화면에서는 쓰지 않는다
    }

    /** 등/하원 시각 저장 결과 — 화면 입력칸의 data-original을 서버 값으로 다시 맞추는 데 쓴다 */
    @Data
    public static class TimeRespDTO {
        private Integer diaryKey;
        private String inTime;   // "HH:mm" (미기록이면 null)
        private String outTime;
    }

    /** 독서일지 화면 조회 응답 */
    @Data
    public static class DiaryViewRespDTO {
        private List<RowDTO> rows;
        // 태도 체크박스 목록(use_yn=1만) — 실시간 모니터링과 같은 마스터를 그대로 쓴다
        private List<MonitorRespDTO.AttitudeCodeDTO> attitudeCodeOptions;
    }

}
