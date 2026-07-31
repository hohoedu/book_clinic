package com.hohoedu.book_clinic._core.utils;

import com.hohoedu.book_clinic._core.auth.CustomUserDetails;
import com.hohoedu.book_clinic._core.handler.exception.Exception403;

/**
 * 센터 권한 정책 — 본사(HQ)만 가능한 작업을 서버에서 막기 위한 공통 판정.
 *
 * 화면에서 버튼을 숨기는 것만으로는 API를 직접 호출하는 걸 막지 못하므로, 본사 전용 기능은
 * 반드시 컨트롤러에서 {@link #assertHq}로 한 번 더 검사한다.
 */
public class CenterPolicy {

    /** 본사 센터 코드 — 마스터 도서(content) 편집, 권장도서 순위 변경 등 전사 공통 데이터의 소유 센터 */
    public static final String HQ_CENTER_CODE = "PUS001";

    private CenterPolicy() {}

    public static boolean isHq(CustomUserDetails userDetails) {
        return userDetails != null
                && HQ_CENTER_CODE.equals(userDetails.getLoginUser().getCenterCode());
    }

    /** 본사 직원이 아니면 403 — 본사 전용 쓰기 API 진입부에서 호출한다 */
    public static void assertHq(CustomUserDetails userDetails) {
        if (!isHq(userDetails)) {
            throw new Exception403("본사에서만 변경할 수 있습니다.");
        }
    }
}
