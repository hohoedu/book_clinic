package com.hohoedu.book_clinic.payment.inicis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * KG이니시스 연동 설정 (application-{dev,prod}.yml의 inicis 블록)
 *
 * 테스트 → 실서비스 전환은 "키값만 바꾸면 되는" 일이 아니다. mid/signKey/apiKey가 한 세트로
 * 바뀌고 환불 API 호스트도 달라지며, 실 MID는 결제 도메인 등록과 API 접근 IP 화이트리스트가
 * 선행되어야 한다. 그래서 값을 코드에 두지 않고 프로파일별 설정으로 전부 밖으로 뺐다.
 *
 * 승인(authUrl)·망취소(netCancelUrl) 주소는 여기에 없다. 결제창 인증 응답이 그 주소를 함께
 * 내려주므로 응답값을 그대로 쓴다 — 하드코딩하면 이니시스가 엔드포인트를 옮길 때 같이 깨진다.
 */
@Slf4j
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "inicis")
public class InicisProperties {

    /** 테스트 상점 여부. 로그/화면 경고에만 쓰고 분기 로직에는 쓰지 않는다(엔드포인트는 아래 값으로 갈린다) */
    private boolean testMode = true;

    /** 상점 아이디 */
    private String mid;

    /** 승인 요청 서명(verification)에 들어가는 키 */
    private String signKey;

    /** 환불(iniapi) 전용 키. 결제창 signKey와 다른 값이다 */
    private String apiKey;

    /** 환불 API 요청 암호화 IV */
    private String apiIv;

    /** 전액취소 API 엔드포인트 */
    private String refundUrl;

    /** 부분취소 API 엔드포인트 — 전액취소와 파라미터만 다른 게 아니라 URL 자체가 다르다 */
    private String partialRefundUrl;

    /**
     * 거래조회(v2/pg/inquiry) API 엔드포인트 — PaymentCleanupJob이 READY 방치 건을 닫기 전에
     * "실제로 승인이 났는데 콜백만 유실된 건 아닌지" 확인할 때 쓴다(2026-08-07).
     */
    private String inquiryUrl;

    /** 결제창이 인증 결과를 던지는 주소 */
    private String returnUrl;

    /** 사용자가 결제창을 닫았을 때 호출되는 주소 */
    private String closeUrl;

    /**
     * 환불 API에 넘기는 clientIp. 사용자의 IP가 아니라 "이 서버의 아웃바운드 IP"다.
     * 이니시스가 화이트리스트와 대조하는 값이라 요청에서 뽑아 넣으면 전부 거절된다.
     * 운영 서버가 나가는 IP를 상점관리자에 등록하고 같은 값을 여기 적는다.
     */
    private String clientIp;

    /** 승인/취소 HTTP 타임아웃(ms). 이 시간을 넘긴 승인은 망취소 대상이다 */
    private int timeoutMs = 15000;

    // ─────────────────── 자동결제(빌링) 전용 설정 (2026-09-07) ───────────────────
    //
    // 빌링은 일반 결제와 상점 자체가 다르다. 이니시스는 빌키 발급/승인용 MID를 따로 발급하며
    // (테스트 MID는 INIBillTst), signKey·apiKey도 그 MID 전용 값이다. 일반 MID의 키로 빌링
    // API를 부르면 전부 거절되므로 값을 섞지 않고 별도 필드로 둔다.
    //
    // 일반 결제 설정을 지우지 않고 남겨두는 이유: 자동결제로 전면 전환해도 과거 일시불 건의
    // 환불·거래조회는 그 MID로 계속 나가야 한다(취소는 원 결제 상점으로만 가능하다).

    /** 빌링 전용 상점 아이디. 비어 있으면 자동결제 기능 자체를 못 쓴다 */
    private String billingMid;

    /** 빌링 승인 API 전용 키. 일반 결제의 apiKey와 다른 값이다 */
    private String billingApiKey;

    /**
     * 모바일 빌키 발급창 주소. 일반 모바일 결제창(mobile.inicis.com/smart/payment/)과
     * 요청 URL·파라미터가 모두 다른 별도 모듈이라 값을 따로 둔다.
     *
     * [매뉴얼 대조 필요] 이 주소와 폼 파라미터 이름은 이니시스 모바일 빌키발급 매뉴얼
     * (manual.inicis.com/mobile/mo-bill.html, 상점관리자 배포본)과 반드시 대조해야 한다.
     * 실 빌링 MID를 발급받는 시점에 함께 확인한다.
     */
    private String billingMobileUrl;

    /** 빌링 승인 API 엔드포인트 (v2/pg/billing) */
    private String billingUrl;

    /**
     * 빌링 승인 요청 data에 넣는 상점 도메인(url). 이니시스가 상점 등록 정보와 대조하는 값이라
     * 요청마다 달라지면 안 된다 — 그래서 요청에서 뽑지 않고 설정으로 고정한다.
     */
    private String billingSiteUrl;

    /** 카드등록(빌키 발급) 결제창이 인증 결과를 던지는 주소 */
    private String billingReturnUrl;

    /** 카드등록 결제창을 사용자가 닫았을 때 호출되는 주소 */
    private String billingCloseUrl;

    /**
     * 운영에서 값이 비어 있으면 결제가 조용히 실패하는 대신 기동 시점에 드러나게 한다.
     * 실결제인데 mid가 비어 있는 채로 서비스가 떠 있는 상황이 제일 위험하기 때문이다.
     * 개발 단계에서는 apiKey 없이도 승인까지는 테스트할 수 있어야 해서 경고만 남긴다.
     */
    @PostConstruct
    void validate() {
        if (testMode) {
            if (isBlank(billingMid)) {
                log.warn("[이니시스] 테스트 모드 — billing-mid가 비어 있어 자동결제(카드등록/재청구)는 동작하지 않습니다.");
            }
            if (isBlank(apiKey)) {
                log.warn("[이니시스] 테스트 모드 — apiKey가 비어 있어 환불(취소) 호출은 실패합니다. 상점관리자에서 발급 후 채우세요.");
            }
            return;
        }
        if (isBlank(mid) || isBlank(signKey) || isBlank(apiKey) || isBlank(returnUrl)) {
            throw new IllegalStateException(
                    "[이니시스] 운영 설정이 비어 있습니다. mid/signKey/apiKey/returnUrl을 채운 뒤 기동하세요.");
        }
        // 자동결제 전환 이후 앱의 신규 결제 경로는 빌링뿐이다. 이 값들이 비어 있으면 결제 자체가
        // 안 되므로 일반 결제 설정과 같은 무게로 기동을 막는다.
        if (isBlank(billingMid) || isBlank(billingApiKey) || isBlank(billingUrl)
                || isBlank(billingMobileUrl) || isBlank(billingReturnUrl)) {
            throw new IllegalStateException(
                    "[이니시스] 빌링(자동결제) 운영 설정이 비어 있습니다. billing-mid/billing-api-key/"
                            + "billing-url/billing-mobile-url/billing-return-url을 채운 뒤 기동하세요.");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
