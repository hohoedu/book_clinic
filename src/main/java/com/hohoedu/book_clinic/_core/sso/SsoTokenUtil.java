package com.hohoedu.book_clinic._core.sso;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.hohoedu.book_clinic._core.utils.KstClock;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;

/**
 * 올패스 ↔ 호호책방 SSO 토큰 브릿지. 서로 다른 도메인이라 세션 쿠키를 공유할 수 없어,
 * 링크 클릭 시 30초짜리 RS256 서명 토큰을 발급해 상대 서버의 콜백 URL로 넘긴다.
 *
 * 서명은 자기 개인키(sso.private-key)로, 검증은 상대 공개키(sso.peer-public-key)로 한다 —
 * 두 앱이 같은 키를 공유하지 않으므로 한쪽이 뚫려도 반대편 토큰을 위조할 수 없다.
 * 재사용(replay) 방지는 서명과 별개로 sso_ticket 테이블(all_pass와 공유하는 DB)에서
 * jti를 원자적으로 1회 소비하는 방식으로 처리한다.
 */
@Component
@RequiredArgsConstructor
public class SsoTokenUtil {

    private static final String ISSUER = "book-clinic";
    private static final Duration TTL = Duration.ofSeconds(30);

    private final SsoTicketRepository ssoTicketRepository;

    @Value("${sso.private-key}")
    private String privateKeyBase64;

    @Value("${sso.peer-public-key}")
    private String peerPublicKeyBase64;

    public String issue(String userId, String redirectUrl) {
        String safeRedirectUrl = sanitizeRedirectUrl(redirectUrl);
        String jti = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant exp = now.plus(TTL);

        ssoTicketRepository.insert(jti, ISSUER, userId, safeRedirectUrl,
                LocalDateTime.ofInstant(exp, KstClock.ZONE));

        return Jwts.builder()
                .id(jti)
                .subject(userId)
                .claim("redirectUrl", safeRedirectUrl)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(privateKey())
                .compact();
    }

    /** 검증 성공 시 {@link SsoPrincipal} 반환, 실패(서명/만료/재사용) 시 예외 */
    public SsoPrincipal verify(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(peerPublicKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 SSO 토큰입니다.", e);
        }

        int consumed = ssoTicketRepository.consume(claims.getId());
        if (consumed != 1) {
            throw new IllegalArgumentException("이미 사용됐거나 만료된 SSO 토큰입니다.");
        }

        return new SsoPrincipal(claims.getSubject(), sanitizeRedirectUrl(claims.get("redirectUrl", String.class)));
    }

    /**
     * open redirect 방지 — redirectUrl은 반드시 이 앱 내부의 상대경로여야 한다.
     * 절대 URL("https://...")이나 프로토콜 상대 URL("//evil.com")은 로그인 성공 후
     * 외부 사이트로 튕겨내는 피싱에 악용될 수 있어 전부 걸러내고 기본값(null)으로 무시한다.
     */
    private static String sanitizeRedirectUrl(String redirectUrl) {
        if (redirectUrl == null || redirectUrl.isBlank()) {
            return null;
        }
        if (!redirectUrl.startsWith("/") || redirectUrl.startsWith("//") || redirectUrl.contains("://")) {
            return null;
        }
        return redirectUrl;
    }

    private PrivateKey privateKey() {
        try {
            byte[] der = pemToDer(new String(Base64.getDecoder().decode(privateKeyBase64)));
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("SSO 개인키 로딩 실패", e);
        }
    }

    private PublicKey peerPublicKey() {
        try {
            byte[] der = pemToDer(new String(Base64.getDecoder().decode(peerPublicKeyBase64)));
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("SSO 상대 공개키 로딩 실패", e);
        }
    }

    private static byte[] pemToDer(String pem) {
        String body = pem.replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }

    public record SsoPrincipal(String userId, String redirectUrl) {
    }
}
