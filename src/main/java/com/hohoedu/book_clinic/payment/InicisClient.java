package com.hohoedu.book_clinic.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * KG이니시스 HTTP 호출 담당 — 승인 / 망취소 / 환불.
 *
 * 이 클래스는 통신과 서명만 한다. 금액 검증·상태 전이·이용권 발급 같은 판단은 전부
 * PaymentService에 있다. PG 응답을 어디까지 믿을지 결정하는 코드가 통신 코드와 섞이면
 * "응답이 성공이라길래 성공 처리했다"는 형태의 버그가 숨을 자리가 생기기 때문이다.
 *
 * [반환값] 파싱된 Map과 응답 원문을 함께 돌려준다. 원문은 erp_bookstore_payment_log에
 * 그대로 적재해야 하는데, Map으로 바꾼 뒤 다시 직렬화하면 이니시스가 실제로 보낸 바이트와
 * 달라져서 분쟁 시 증거 가치가 떨어진다.
 *
 * [주의 — 매뉴얼 대조 필요] 서명 규칙과 파라미터 이름은 이니시스 연동 매뉴얼(상점관리자에서
 * 내려받는 버전)과 반드시 대조해야 한다. 특히 환불 API는 결제창과 규칙이 다르고
 * (hashData는 SHA512, 성공 resultCode도 승인과 다름) 매뉴얼 개정에 따라 바뀐 이력이 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InicisClient {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final InicisProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 호출 결과 — 파싱본과 원문, HTTP 상태를 함께 들고 다닌다 */
    public record Result(int httpStatus, Map<String, Object> body, String rawBody) {

        public String get(String key) {
            Object v = body == null ? null : body.get(key);
            return v == null ? null : String.valueOf(v);
        }

        /** 결제창 승인 계열의 성공 코드 */
        public boolean isApproveSuccess() {
            return "0000".equals(get("resultCode"));
        }

        /** 환불(iniapi) 계열의 성공 코드 — 승인과 코드 체계가 다르다 */
        public boolean isRefundSuccess() {
            return "00".equals(get("resultCode"));
        }

        /** 모바일 결제창 계열의 성공 코드. 응답 키 이름도 P_ 접두어가 붙어 PC와 다르다 */
        public boolean isMobileSuccess() {
            return "00".equals(get("P_STATUS"));
        }

        /** 거래조회(v2) 계열의 성공 코드 — "조회 자체가 됐다"는 뜻이지 "승인됐다"는 뜻이 아니다 */
        public boolean isInquirySuccess() {
            return "SUCCESS".equals(get("resultCode"));
        }

        /**
         * 조회된 거래가 실제로 승인 상태인지 — APPROVAL(정상승인)과 PART_CANCEL(부분취소, 원거래
         * 자체는 승인된 것)을 모두 승인으로 본다. CANCEL(전액취소)이나 그 외는 승인 아님으로 본다.
         */
        public boolean isApproved() {
            String status = get("transactionStatus");
            return "APPROVAL".equals(status) || "PART_CANCEL".equals(status);
        }
    }

    /**
     * 승인 요청 — 결제창 인증까지 끝난 건을 실제 결제로 확정한다.
     *
     * authUrl은 인증 응답이 내려준 값을 그대로 넘긴다(설정에 없는 이유는 InicisProperties 참고).
     * 앱이 보낸 authUrl을 그대로 쓰면 공격자가 자기 서버 주소를 넣어 "성공" 응답을 위조할 수
     * 있으므로, 호출 전에 이니시스 도메인인지 검증한 뒤 들어와야 한다(PaymentService 담당).
     */
    public Result approve(String authUrl, String authToken) {
        String timestamp = String.valueOf(System.currentTimeMillis());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mid", props.getMid());
        form.add("authToken", authToken);
        form.add("timestamp", timestamp);
        form.add("signature", sha256("authToken=" + authToken + "&timestamp=" + timestamp));
        form.add("verification",
                sha256("authToken=" + authToken + "&signKey=" + props.getSignKey() + "&timestamp=" + timestamp));
        form.add("charset", "UTF-8");
        form.add("format", "JSON");

        return post(authUrl, form);
    }

    /**
     * 망취소 — 승인은 났는데 우리 쪽에서 확정할 수 없을 때(금액 불일치, DB 실패, 타임아웃)
     * 카드사 승인을 되돌린다. 이걸 빠뜨리면 고객 카드에는 승인이 남고 우리 DB에는 결제가
     * 없는 상태가 되므로, 승인 이후 단계의 모든 실패 경로는 반드시 여기로 와야 한다.
     *
     * 망취소마저 실패하면 자동으로 복구할 방법이 없다. 로그에 NET_CANCEL 실패로 남겨
     * 사람이 상점관리자에서 수동 취소하도록 한다.
     */
    public Result netCancel(String netCancelUrl, String authToken) {
        String timestamp = String.valueOf(System.currentTimeMillis());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mid", props.getMid());
        form.add("authToken", authToken);
        form.add("timestamp", timestamp);
        form.add("signature", sha256("authToken=" + authToken + "&timestamp=" + timestamp));
        form.add("verification",
                sha256("authToken=" + authToken + "&signKey=" + props.getSignKey() + "&timestamp=" + timestamp));
        form.add("charset", "UTF-8");
        form.add("format", "JSON");

        return post(netCancelUrl, form);
    }

    /**
     * 환불(취소) — 전액이면 partialAmount에 null, 부분환불이면 이번에 돌려줄 금액을 넣는다.
     *
     * clientIp는 사용자 IP가 아니라 이 서버의 아웃바운드 IP다(설정값). 이니시스가 화이트리스트와
     * 대조하는 값이라 요청에서 뽑아 넣으면 전부 거절된다.
     */
    public Result refund(String tid, String reason, Integer partialAmount, int remainAfterRefund) {
        String clientIp = props.getClientIp();
        boolean partial = partialAmount != null;
        // 실제 요청서비스 값은 소문자 시작(fixed value) — "Refund"/"PartialRefund"가 아니다
        // (2026-08-05, 상점관리자 API 문서 확인).
        String type = partial ? "partialRefund" : "refund";
        // 환불(webApi) 계열은 승인/결제창과 달리 timestamp를 유닉스 타임(초/밀리초)이 아니라
        // yyyyMMddHHmmss(14자리) 형태로 요구한다. 밀리초(13자리)·초단위(10자리) 모두
        // 이니시스가 ERR101(Length 오류)로 거절했다.
        String timestamp = LocalDateTime.now(KST).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        // v2 취소 API는 요청을 최상위 필드(mid/type/timestamp/clientIp/hashData)와 그 안에 중첩된
        // data(tid/msg/price/confirmPrice)로 나눈다. 전액취소는 data에 tid/msg만 있으면 된다
        // (price/confirmPrice는 필수가 아니라 아예 안 넣음). currency/tax/taxFree는 필수 항목이
        // 아니라고 확인해서 넣지 않는다.
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tid", tid);
        data.put("msg", reason);
        if (partial) {
            data.put("price", partialAmount);            // 이번에 취소할 금액
            data.put("confirmPrice", remainAfterRefund);  // 취소 후 남을 금액 (대조용)
        }

        String dataJson;
        try {
            dataJson = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new IllegalStateException("[이니시스] 취소 요청 data 직렬화 실패", e);
        }

        // hashData = SHA512(INIAPIKey + mid + type + timestamp + data의 JSON 문자열) — 위 dataJson과
        // 정확히 같은 문자열이어야 한다. body에도 이 Map을 그대로 중첩시켜서 실제 전송 시 다시
        // 직렬화되는 결과가 여기서 만든 dataJson과 어긋나지 않게 한다(둘 다 Jackson 기본 컴팩트 출력).
        String hashData = sha512(props.getApiKey() + props.getMid() + type + timestamp + dataJson);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mid", props.getMid());
        body.put("type", type);
        body.put("timestamp", timestamp);
        body.put("clientIp", clientIp);
        body.put("hashData", hashData);
        body.put("data", data);

        // 전액취소와 부분취소는 파라미터(type)만 다른 게 아니라 엔드포인트 자체가 다르다
        // (v2/pg/refund vs v2/pg/partialRefund) — type만 바꿔서 같은 URL로 보내면 거절된다.
        String url = partial ? props.getPartialRefundUrl() : props.getRefundUrl();
        return postJson(url, body);
    }

    /**
     * 거래조회 — READY로 방치된 주문을 닫기 전에 "카드사 승인은 났는데 콜백만 유실된 건 아닌지"
     * 확인할 때 쓴다(2026-08-07, PaymentCleanupJob). 승인 콜백을 아예 못 받은 상태라 tid를
     * 모르므로 oid(우리 주문번호)로 조회한다 — 매뉴얼상 tid/oid 중 하나만 있으면 된다.
     *
     * refund()와 같은 v2(JSON) 계열이라 요청 구조도 같다: 최상위(mid/type/timestamp/clientIp/
     * hashData) + 중첩 data(oid). hashData = SHA512(INIAPIKey + mid + type + timestamp + data의
     * JSON 문자열) — dataJson과 정확히 같은 문자열이어야 한다(매뉴얼: manual.inicis.com/pay/etc-inquiry.html).
     */
    public Result inquiry(String oid) {
        String clientIp = props.getClientIp();
        String type = "inquiry";
        String timestamp = LocalDateTime.now(KST).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("oid", oid);

        String dataJson;
        try {
            dataJson = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new IllegalStateException("[이니시스] 거래조회 요청 data 직렬화 실패", e);
        }

        String hashData = sha512(props.getApiKey() + props.getMid() + type + timestamp + dataJson);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mid", props.getMid());
        body.put("type", type);
        body.put("timestamp", timestamp);
        body.put("clientIp", clientIp);
        body.put("hashData", hashData);
        body.put("data", data);

        return postJson(props.getInquiryUrl(), body);
    }

    // ─────────────── 결제창 호출용 서명 (승인 요청과는 별개의 규칙) ───────────────
    //
    // 승인은 authToken을 재료로 쓰지만, 결제창은 아직 authToken이 없어서 주문번호·금액·시각으로
    // 서명한다. 규칙이 다르므로 위 approve()의 서명을 여기에 재사용하면 결제창이 열리지 않는다.

    /** 결제창 signature — 위변조 검증용 */
    public String stdPaySignature(String oid, int price, String timestamp) {
        return sha256("oid=" + oid + "&price=" + price + "&timestamp=" + timestamp);
    }

    /** 결제창 verification — signKey가 들어가 상점 본인 확인까지 겸한다 */
    public String stdPayVerification(String oid, int price, String timestamp) {
        return sha256("oid=" + oid + "&price=" + price + "&signKey=" + props.getSignKey() + "&timestamp=" + timestamp);
    }

    /** 결제창 mKey — signKey 자체의 해시 */
    public String stdPayMKey() {
        return sha256(props.getSignKey());
    }

    /**
     * 모바일 결제창 승인 — PC와 절차 자체가 다르다.
     *
     * PC는 인증 응답의 authToken을 authUrl로 보내 승인하지만, 모바일은 인증 응답에 들어 있는
     * P_REQ_URL을 그대로 호출하는 것이 승인이다. 그래서 서명도 authToken도 쓰지 않고
     * mid와 tid만 보낸다 — 이 주소를 아는 것 자체가 인증을 통과했다는 증거이기 때문이다.
     *
     * 그래서 앱이 보낸 P_REQ_URL을 그대로 믿으면 안 된다. 호출 전에 이니시스 도메인인지
     * 검증해야 하며, 그 검증은 PaymentService가 한다.
     */
    public Result approveMobile(String reqUrl, String tid) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("P_MID", props.getMid());
        form.add("P_TID", tid);
        return post(reqUrl, form);
    }

    private Result post(String url, MultiValueMap<String, String> form) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.getTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(props.getTimeoutMs()));

        RestClient client = RestClient.builder().requestFactory(factory).build();

        var response = client.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toEntity(String.class);

        String raw = response.getBody();
        return new Result(response.getStatusCode().value(), parse(raw), raw);
    }

    /** v2 취소 API 전용 — JSON 바디로 보낸다(위 post()의 form-urlencoded와 다름) */
    private Result postJson(String url, Map<String, Object> body) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.getTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(props.getTimeoutMs()));

        RestClient client = RestClient.builder().requestFactory(factory).build();

        String raw;
        try {
            raw = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("[이니시스] 취소 요청 바디 직렬화 실패", e);
        }

        var response = client.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(raw)
                .retrieve()
                .toEntity(String.class);

        String respRaw = response.getBody();
        return new Result(response.getStatusCode().value(), parse(respRaw), respRaw);
    }

    /**
     * 응답 파싱 — 이니시스는 창구마다 응답 형식이 다르다.
     *   · PC 승인/환불 : JSON (format=JSON을 줬을 때)
     *   · 모바일 승인   : key=value&key=value 쿼리스트링
     * 그래서 본문 첫 글자로 형식을 판별한다.
     *
     * 파싱 실패를 예외로 던지면 원문 로그조차 못 남기고 끝나므로, 빈 Map으로 넘기고
     * 판단은 호출부가 원문을 보고 하게 둔다.
     */
    private Map<String, Object> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new LinkedHashMap<>();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("{")) {
            try {
                return objectMapper.readValue(trimmed, new com.fasterxml.jackson.core.type.TypeReference<>() {
                });
            } catch (Exception e) {
                log.warn("[이니시스] JSON 파싱 실패 — 원문으로 판단한다: {}", raw);
                return new LinkedHashMap<>();
            }
        }
        return parseQueryString(trimmed);
    }

    /** 모바일 승인 응답(P_STATUS=00&P_TID=...&...) 파싱 */
    private Map<String, Object> parseQueryString(String raw) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = pair.substring(0, eq).trim();
            String value = pair.substring(eq + 1).trim();
            try {
                value = java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                // 디코딩이 안 되면 원문 그대로 둔다 — 값이 사라지는 것보다 낫다
            }
            map.put(key, value);
        }
        return map;
    }

    private String sha256(String plain) {
        return digest("SHA-256", plain);
    }

    private String sha512(String plain) {
        return digest("SHA-512", plain);
    }

    private String digest(String algorithm, String plain) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            return HexFormat.of().formatHex(md.digest(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("[이니시스] 서명 생성 실패: " + algorithm, e);
        }
    }
}
