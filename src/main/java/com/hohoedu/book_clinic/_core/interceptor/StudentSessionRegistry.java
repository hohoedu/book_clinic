package com.hohoedu.book_clinic._core.interceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 학생별로 "지금 유효한" HttpSession id를 1개만 기억한다(2026-08-26) — 같은 studentId로 다른
 * 기기에서 새로 로그인하면 이전 기기의 세션이 다음 요청부터 무효 판정을 받게 하기 위함이다.
 * 직원의 퇴실 처리(MonitorService.exitSession)도 여기 등록을 지워서, 문제 풀이 중이던 기기가
 * 다음 요청(또는 폴링)에서 바로 로그아웃되게 한다.
 *
 * 서버 재시작 시 초기화되는 인메모리 저장소다 — 이 앱은 단일 인스턴스 운영이라 지금은 이 정도로
 * 충분하다(여러 인스턴스로 스케일아웃하면 인스턴스마다 따로 논다).
 */
@Component
public class StudentSessionRegistry {

    private final Map<String, String> activeSessionByStudent = new ConcurrentHashMap<>();

    /** 로그인 성공 시 호출 — 이 studentId의 "현재 유효한" 세션을 이 sessionId 하나로 교체한다 */
    public void register(String studentId, String sessionId) {
        activeSessionByStudent.put(studentId, sessionId);
    }

    /** 이 sessionId가 지금 이 studentId의 유효한 세션인지 — 등록된 적 없으면(퇴실 등으로 지워졌으면) false */
    public boolean isActive(String studentId, String sessionId) {
        return sessionId.equals(activeSessionByStudent.get(studentId));
    }

    /** 퇴실 처리 시 호출 — 등록을 지워 다음 요청부터 강제 로그아웃되게 한다 */
    public void clear(String studentId) {
        activeSessionByStudent.remove(studentId);
    }
}
