package com.hohoedu.book_clinic._core.view;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 정독 달력 화면 — i-with 앱(책방 메인)이 WebView로 띄우는 경로 (2026-09-21).
 *
 * 올패스(all_pass)가 /calendar.html 로 내주던 화면을 호호책방으로 옮기면서, 내용도 올패스의
 * 수업 주차표가 아니라 책방 회차(예약하기 화면과 같은 데이터)로 바꿨다.
 *
 * 이 매핑은 빈 화면만 내린다(permitAll). 실제 데이터는 JS가 /app/calendar(POST)로 가져오고,
 * 그쪽이 세션의 studentId 를 요구한다 — 앱이 WebView 쿠키에 JSESSIONID 를 심어 보낸다.
 */
@Controller
public class CalendarViewController {

    @GetMapping("/calendar")
    public String calendar() {
        return "calendar";
    }
}
