package com.hohoedu.book_clinic.payment;

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

    /**
     * 운영에서 값이 비어 있으면 결제가 조용히 실패하는 대신 기동 시점에 드러나게 한다.
     * 실결제인데 mid가 비어 있는 채로 서비스가 떠 있는 상황이 제일 위험하기 때문이다.
     * 개발 단계에서는 apiKey 없이도 승인까지는 테스트할 수 있어야 해서 경고만 남긴다.
     */
    @PostConstruct
    void validate() {
        if (testMode) {
            if (isBlank(apiKey)) {
                log.warn("[이니시스] 테스트 모드 — apiKey가 비어 있어 환불(취소) 호출은 실패합니다. 상점관리자에서 발급 후 채우세요.");
            }
            return;
        }
        if (isBlank(mid) || isBlank(signKey) || isBlank(apiKey) || isBlank(returnUrl)) {
            throw new IllegalStateException(
                    "[이니시스] 운영 설정이 비어 있습니다. mid/signKey/apiKey/returnUrl을 채운 뒤 기동하세요.");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
