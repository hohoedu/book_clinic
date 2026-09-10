package com.hohoedu.book_clinic.app._dto;

import lombok.Data;

/**
 * i-with 책방 메인 화면 응답. 단일 쿼리(app.xml#selectBookstoreMain) 한 행을 그대로 담는다.
 * 값이 없는 항목은 null (학생이 예약/이용권/독서기록이 없을 수 있음).
 */
@Data
public class BookstoreMainRespDTO {

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
