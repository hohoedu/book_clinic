package com.hohoedu.book_clinic._core.interceptor;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 학생용 쓰기 요청이 등록된 센터 기기에서 왔는지 확인한다 (2026-08-20).
 *
 * [현재 비활성화됨, 2026-08-21] 누구나 접속 가능해야 한다는 사업 판단으로 기기 등록 검사를
 * 껐다. 원래 검사 로직(KioskService#resolveFromRequest 대조)은 git 이력에 남아 있으니,
 * 되돌릴 땐 preHandle 본문만 그 로직으로 되돌리면 된다.
 *
 * [원래 왜 필요했는가] 학생 신원은 appId로 확인하는데 그건 학생증에 인쇄된 값이라 비밀이 아니다.
 * QR 스캔이든 직접 입력이든 서버가 받는 값이 같아서, appId만으로는 "본인"을 증명할 수 없다.
 * 문제풀이·출석이 센터 기기에서만 일어난다는 운영 전제를 근거로, 등록된 기기에서 온 요청만
 * 통과시켰다 — 기기 안에서는 appId만으로 충분하고, 기기 밖에서는 남의 appId를 알아도 못 들어온다.
 * 끄고 나면 이 전제가 사라지므로, 등록된 기기가 아니어도 남의 appId로 접근이 가능해진다.
 *
 * 이 인터셉터는 "어디서 왔는가"만 본다. "그 학생이 이 센터 소속인가"는 학생을 찾아내는 입구에서
 * {@link com.hohoedu.book_clinic.kiosk.KioskService#assertSameCenter}가 보는데, 지금은
 * ALL_CENTERS로 고정 통과시키므로 그 대조도 함께 꺼진 상태다.
 */
@Component
public class KioskTokenInterceptor implements HandlerInterceptor {

    /** 요청을 통과시킨 기기의 센터코드를 뒤 단계에서 읽을 수 있게 남겨둔다 */
    public static final String ATTR_CENTER_CODE = "kioskCenterCode";

    /**
     * 전 센터 키로 등록된 기기임을 나타내는 표식.
     *
     * 센터코드 자리에 null을 넣으면 "인터셉터를 안 탄 경로"와 구분되지 않아, 검사가 조용히
     * 통과하거나 조용히 막히는 쪽으로 갈린다. 값을 명시적으로 넣어 둘을 갈라놓는다.
     */
    public static final String ALL_CENTERS = "*";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 기기 등록 검사를 일시 비활성화(2026-08-21, 사업 판단) — 누구나 접속 가능해야 해서
        // 기기 대조를 건너뛴다. ALL_CENTERS로 표시해 KioskService#assertSameCenter의 센터 대조도
        // 같이 통과시킨다. 되돌릴 땐 이 메서드 본문만 원래 검사 로직으로 되돌리면 된다.
        request.setAttribute(ATTR_CENTER_CODE, ALL_CENTERS);
        return true;
    }
}
