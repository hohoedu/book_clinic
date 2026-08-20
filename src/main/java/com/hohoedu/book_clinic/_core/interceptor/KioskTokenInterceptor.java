package com.hohoedu.book_clinic._core.interceptor;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.hohoedu.book_clinic._core.handler.exception.Exception403;
import com.hohoedu.book_clinic.kiosk.KioskService;
import com.hohoedu.book_clinic.kiosk._dto.KioskRespDTO;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 학생용 쓰기 요청이 등록된 센터 기기에서 왔는지 확인한다 (2026-08-20).
 *
 * [왜 필요한가] 학생 신원은 appId로 확인하는데 그건 학생증에 인쇄된 값이라 비밀이 아니다.
 * QR 스캔이든 직접 입력이든 서버가 받는 값이 같아서, appId만으로는 "본인"을 증명할 수 없다.
 * 문제풀이·출석이 센터 기기에서만 일어난다는 운영 전제를 근거로, 등록된 기기에서 온 요청만
 * 통과시킨다 — 기기 안에서는 appId만으로 충분하고, 기기 밖에서는 남의 appId를 알아도 못 들어온다.
 *
 * [읽기는 막지 않는다] 로그인 화면이나 설치 안내 같은 GET 화면까지 막으면, 등록되지 않은 기기가
 * "등록해주세요" 안내조차 띄울 수 없다. 세션을 만들거나 상태를 바꾸는 요청만 막는다.
 *
 * [학부모 앱은 대상이 아니다] /app/**, /payment/**는 개인 휴대폰에서 쓰고 appId+비밀번호로
 * 인증하므로 기기 키를 요구하지 않는다.
 *
 * 이 인터셉터는 "어디서 왔는가"만 본다. "그 학생이 이 센터 소속인가"는 학생을 찾아내는 입구에서
 * {@link KioskService#assertSameCenter}가 본다.
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

    private final KioskService kioskService;

    // KioskService가 이 클래스의 상수를 참조해 순환이 생기므로 지연 주입한다
    public KioskTokenInterceptor(@Lazy KioskService kioskService) {
        this.kioskService = kioskService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 화면을 보여주기만 하는 요청은 통과 — 등록 안 된 기기도 "등록해주세요" 안내는 떠야 한다.
        // 세션을 만들거나(POST /student/login) 상태를 바꾸는 요청만 막으면 충분하다.
        if (isReadOnly(request.getMethod())) {
            return true;
        }

        KioskRespDTO.ResolvedDTO resolved = kioskService.resolveFromRequest(request);
        if (resolved == null) {
            throw new Exception403("등록되지 않은 기기입니다. 센터 기기에서 이용해주세요.");
        }
        String centerCode = resolved.getCenterCode();
        request.setAttribute(ATTR_CENTER_CODE, centerCode == null ? ALL_CENTERS : centerCode);
        return true;
    }

    private boolean isReadOnly(String method) {
        return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
    }
}
