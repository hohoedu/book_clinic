package com.hohoedu.book_clinic.appsend._dto;

import java.util.List;

import com.hohoedu.book_clinic.diary._dto.DiaryRespDTO;

import lombok.Data;

/** 독서 결과 발송 화면 응답 DTO 모음 (2026-09-22) */
public class AppSendRespDTO {

    /**
     * 목록 화면 응답.
     *
     * 회차(slots)를 목록과 따로 내리는 건, 그날 그 회차에 학생이 한 명도 없어도 드롭다운에는
     * 회차가 다 떠 있어야 하기 때문이다. 행에서 회차를 뽑아 채우면 학생 없는 회차가 아예
     * 선택지에서 사라져, 직원이 "이 회차가 없는 건지 학생이 없는 건지" 구분할 수 없다.
     */
    @Data
    public static class SendViewRespDTO {
        private List<SlotDTO> slots;
        private List<RowDTO> rows;
    }

    /** 그날 그 센터의 회차 하나 — 학생이 없어도 내려온다 */
    @Data
    public static class SlotDTO {
        private Integer seq;     // 회차 번호 (1,2,3 …)
        private String label;    // "1교시(14:00 ~ 15:00)"
    }

    /**
     * 발송 목록 1행 = 그날 예약 1건.
     *
     * 발송 상태는 독서일지 헤더(erp_bookstore_diary)의 is_send/send_at 그대로다 — 예약 발송을
     * 쓰지 않기로 해서(2026-09-22) 발송/미발송 두 값이면 충분하고, 별도 발송 테이블을 두지 않는다.
     */
    @Data
    public static class RowDTO {
        private Integer reservationId;
        private String studentId;
        private String studentName;
        private String gradeName;      // erp_bookstore_code(gubun='S') 코드명 — "초1" 등
        private Integer slotSeq;       // 회차 번호 — 화면 필터 키
        private String slotLabel;      // "1교시(14:00 ~ 15:00)"

        private Integer sessionId;     // null이면 아직 미입실
        private Integer diaryKey;      // 발송 처리 대상 키 — 일지가 없으면 null

        private Boolean isSend;
        private String sendAt;         // "yyyy-MM-dd HH:mm" — 미발송이면 null

        /** 학생 앱 미가입(app_token 없음)이면 발송 자체가 불가하다 */
        private Boolean hasAppToken;

        /** 그날 읽은 책 — 독서일지와 같은 조회를 그대로 쓴다(DiaryRepository.findDiaryBooks) */
        private List<DiaryRespDTO.BookDTO> books;
    }

    /**
     * 발송 처리 대상 한 건 — is_send 를 올리기 직전에 뽑아 둔다.
     *
     * 갱신 후에 다시 조회하면 "이번에 바뀐 건"과 "원래 발송돼 있던 건"을 구분할 수 없어,
     * 푸시가 이미 보낸 학생에게 한 번 더 나간다. 그래서 대상을 먼저 확정하고 그 키로만 갱신한다.
     */
    @Data
    public static class SendTargetDTO {
        private Integer diaryKey;
        private String studentId;
        private String studentName;
    }
}
