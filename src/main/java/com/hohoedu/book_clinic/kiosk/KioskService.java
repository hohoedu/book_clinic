package com.hohoedu.book_clinic.kiosk;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.handler.exception.Exception403;
import com.hohoedu.book_clinic._core.handler.exception.Exception404;
import com.hohoedu.book_clinic._core.interceptor.KioskTokenInterceptor;
import com.hohoedu.book_clinic._core.utils.CenterPolicy;
import com.hohoedu.book_clinic._core.utils.HashUtils;
import com.hohoedu.book_clinic.kiosk._dto.KioskRespDTO;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 센터 기기 라이선스 — 발급·등록·검사·폐기 (2026-08-20).
 *
 * [무엇을 증명하는가] 이 키는 "누가"가 아니라 "어디서"를 증명한다. 학생 신원은 여전히 appId로
 * 확인하는데 그건 학생증에 인쇄된 값이라 비밀이 아니다. 대신 문제풀이·출석이 센터 기기에서만
 * 일어난다는 운영 전제를 근거로, 등록된 기기에서 온 요청만 통과시킨다. 기기 안에서는 appId만으로
 * 충분하고(카드를 두고 온 학생이 직접 입력해 들어갈 수 있어야 한다), 기기 밖에서는 못 들어온다.
 *
 * [한계] 공용 기기에 저장된 값이라, 그 기기를 물리적으로 만질 수 있는 사람은 꺼낼 수 있다.
 * 이 장치가 막는 것은 "인터넷 아무 데서나 남의 appId로 들어오는 것"이고, 유출되더라도 폭발
 * 반경이 그 센터 하나로 묶이며 폐기로 즉시 끊을 수 있다는 데 의미가 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KioskService {

    /** 쿠키 이름 — 기기 등록 시 서버가 심고, 이후 모든 요청에 자동으로 실린다 */
    public static final String COOKIE_NAME = "KIOSK_DEVICE";

    /**
     * 사람이 읽고 옮겨 적는 키라 헷갈리는 글자를 뺀다(I/O/0/1 제외, Crockford Base32 계열).
     * 32글자 알파벳 16자리 = 80비트 — 무차별 대입은 현실적으로 불가능하다.
     */
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int KEY_LENGTH = 16;
    private static final int GROUP_SIZE = 4;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final KioskRepository kioskRepository;

    public List<KioskRespDTO.CenterOptionDTO> findCenterOptions() {
        return kioskRepository.findCenterOptions(CenterPolicy.HQ_CENTER_CODE);
    }

    public List<KioskRespDTO.LicenseDTO> findLicenses(String centerCode) {
        return kioskRepository.findLicenses(centerCode);
    }

    /**
     * 새 라이선스 발급(본사 전용 — 호출부에서 CenterPolicy.assertHq로 막는다).
     * 키 원문은 이 응답에만 실리고 DB에는 해시만 남는다.
     */
    @Transactional
    public KioskRespDTO.IssuedDTO issue(String centerCode, String label, Integer deviceLimit, String issuedBy) {
        if (label == null || label.isBlank()) {
            throw new Exception400("기기 이름을 입력해주세요.");
        }
        if (label.length() > 50) {
            throw new Exception400("기기 이름은 50자까지 입력할 수 있습니다.");
        }

        // 비어 있으면 전 센터 키 — 본사 테스트 태블릿이 여러 센터를 오갈 수 있게 한다
        String scopedCenterCode = (centerCode == null || centerCode.isBlank()) ? null : centerCode;

        int limit = deviceLimit == null ? 1 : deviceLimit;
        if (limit < 0) {
            throw new Exception400("기기 수는 0 이상이어야 합니다. (0 = 무제한)");
        }

        String licenseKey = generateKey();
        String keyHash = HashUtils.sha256(normalizeKey(licenseKey));

        kioskRepository.insertLicense(scopedCenterCode, keyHash, label.trim(), issuedBy, limit);
        log.info("[기기 라이선스] 발급 — centerCode={}, label={}, 기기수={}, by={}",
                scopedCenterCode == null ? "전 센터" : scopedCenterCode, label, limit, issuedBy);

        KioskRespDTO.IssuedDTO issued = new KioskRespDTO.IssuedDTO();
        issued.setLicenseId(kioskRepository.findIdByHash(keyHash));
        issued.setCenterCode(scopedCenterCode);
        issued.setLabel(label.trim());
        issued.setLicenseKey(licenseKey);
        return issued;
    }

    /**
     * 요청에 실린 기기 쿠키를 읽어 해석한다. 인터셉터와 화면(등록 여부 판단)이 같이 쓴다 —
     * 쿠키를 읽는 방법이 두 곳에 흩어지면 이름이 바뀔 때 한쪽만 고쳐진다.
     */
    public KioskRespDTO.ResolvedDTO resolveFromRequest(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return resolve(cookie.getValue());
            }
        }
        return null;
    }

    /**
     * 기기 토큰으로 소속 센터를 돌려준다. 유효하지 않으면 null —
     * 호출부(인터셉터)가 "값이 없으면 거부"만 하면 되도록 예외를 던지지 않는다.
     */
    public KioskRespDTO.ResolvedDTO resolve(String deviceToken) {
        if (deviceToken == null || deviceToken.isBlank()) {
            return null;
        }
        KioskRespDTO.ResolvedDTO resolved =
                kioskRepository.findActiveDeviceByHash(HashUtils.sha256(deviceToken));
        if (resolved != null) {
            kioskRepository.touchDeviceUsed(resolved.getDeviceId());
        }
        return resolved;
    }

    public List<KioskRespDTO.DeviceDTO> findDevices(Integer licenseId) {
        return kioskRepository.findDevices(licenseId);
    }

    /**
     * 기기 등록 — 입력된 키가 유효하고 한도가 남았으면 이 기기 몫의 토큰을 새로 만들어 준다.
     *
     * 라이선스 키 자체를 쿠키에 담지 않는 이유는, 담아두면 그 기기에서 키 원문을 다시 꺼내
     * 다른 기기를 무제한으로 붙일 수 있기 때문이다. 기기마다 다른 토큰을 주면 기기 단위로
     * 폐기할 수 있고 목록에서 몇 대가 쓰는지도 보인다.
     */
    @Transactional
    public KioskRespDTO.RegisteredDTO register(String licenseKey, String userAgent) {
        String normalized = normalizeKey(licenseKey);
        KioskRespDTO.LicenseDTO license = normalized.isEmpty()
                ? null
                : kioskRepository.findActiveLicenseByHash(HashUtils.sha256(normalized));
        if (license == null) {
            throw new Exception404("등록할 수 없는 키입니다. 본사에서 받은 키를 다시 확인해주세요.");
        }

        // device_limit 0은 무제한(본사 테스트용). 그 외에는 현재 등록 대수와 비교한다.
        int limit = license.getDeviceLimit() == null ? 1 : license.getDeviceLimit();
        if (limit > 0) {
            int registered = kioskRepository.countActiveDevices(license.getLicenseId());
            if (registered >= limit) {
                throw new Exception400("이 키로 등록할 수 있는 기기 수(" + limit + "대)를 모두 썼습니다."
                        + " 쓰지 않는 기기를 폐기하거나 본사에 새 키를 요청해주세요.");
            }
        }

        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String deviceToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        kioskRepository.insertDevice(license.getLicenseId(), HashUtils.sha256(deviceToken), trimUserAgent(userAgent));

        log.info("[기기 라이선스] 기기 등록 — centerCode={}, licenseId={}",
                license.getCenterCode() == null ? "전 센터" : license.getCenterCode(), license.getLicenseId());

        KioskRespDTO.RegisteredDTO registered = new KioskRespDTO.RegisteredDTO();
        registered.setCenterCode(license.getCenterCode());
        registered.setDeviceToken(deviceToken);
        return registered;
    }

    /** 등록 해제 — 기기를 반납하거나 다른 센터로 옮길 때. 그 기기 행만 폐기한다 */
    @Transactional
    public void unregister(String deviceToken, String revokedBy) {
        KioskRespDTO.ResolvedDTO resolved =
                deviceToken == null ? null : kioskRepository.findActiveDeviceByHash(HashUtils.sha256(deviceToken));
        if (resolved == null) {
            throw new Exception404("등록되지 않은 기기입니다.");
        }
        kioskRepository.revokeDevice(resolved.getDeviceId(), revokedBy);
        log.info("[기기 라이선스] 등록 해제 — deviceId={}, by={}", resolved.getDeviceId(), revokedBy);
    }

    @Transactional
    public void revokeDevice(Integer deviceId, String revokedBy) {
        if (kioskRepository.revokeDevice(deviceId, revokedBy) == 0) {
            throw new Exception404("이미 폐기되었거나 존재하지 않는 기기입니다.");
        }
        log.info("[기기 라이선스] 기기 폐기 — deviceId={}, by={}", deviceId, revokedBy);
    }

    /** 키 폐기 — 이 키로 등록된 기기가 전부 함께 막힌다(findActiveDeviceByHash가 라이선스도 본다) */
    @Transactional
    public void revoke(Integer licenseId, String revokedBy) {
        if (kioskRepository.revoke(licenseId, revokedBy) == 0) {
            throw new Exception404("이미 폐기되었거나 존재하지 않는 키입니다.");
        }
        log.info("[기기 라이선스] 키 폐기 — licenseId={}, by={}", licenseId, revokedBy);
    }

    /** user_agent 컬럼이 255자라 넘치면 잘라 담는다 — 기기를 알아보는 단서일 뿐 정확할 필요는 없다 */
    private String trimUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent;
    }

    /**
     * 이 요청을 통과시킨 기기의 센터와 대상 학생의 센터가 같은지 확인한다.
     *
     * 키만으로는 "등록된 어느 기기인가"까지만 알 수 있어서, 대조를 빼두면 DAE001 기기로 PUS002
     * 학생을 퇴실시키거나 그 학생으로 로그인하는 게 그대로 통과한다(실제로 재현됨). 학생을
     * appId로 찾아내는 입구마다 불러야 한다 — 이후 화면들은 세션을 신뢰하므로, 세션이 만들어지는
     * 지점에서 막으면 뒤는 따라온다.
     *
     * 기기 센터코드가 없다는 건 인터셉터를 타지 않는 경로에서 불렸다는 뜻이라 통과시키지 않는다 —
     * 조용히 넘어가면 경로가 하나 바뀌는 순간 검사가 사라진다.
     */
    public void assertSameCenter(HttpServletRequest request, String studentCenterCode) {
        Object kioskCenterCode = request.getAttribute(KioskTokenInterceptor.ATTR_CENTER_CODE);
        if (kioskCenterCode == null) {
            throw new Exception403("이 센터의 학생이 아닙니다. 소속 센터에서 이용해주세요.");
        }
        // 전 센터 키(본사 테스트 태블릿)는 어느 센터 학생이든 처리할 수 있어야 해서 대조를 건너뛴다
        if (KioskTokenInterceptor.ALL_CENTERS.equals(kioskCenterCode)) {
            return;
        }
        if (!kioskCenterCode.equals(studentCenterCode)) {
            throw new Exception403("이 센터의 학생이 아닙니다. 소속 센터에서 이용해주세요.");
        }
    }

    /** XXXX-XXXX-XXXX-XXXX 형태로 만든다 — 전화로 불러주고 받아 적을 수 있어야 한다 */
    private String generateKey() {
        StringBuilder key = new StringBuilder(KEY_LENGTH + KEY_LENGTH / GROUP_SIZE);
        for (int i = 0; i < KEY_LENGTH; i++) {
            if (i > 0 && i % GROUP_SIZE == 0) {
                key.append('-');
            }
            key.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return key.toString();
    }

    /**
     * 하이픈·공백·대소문자는 입력 편의를 위한 것이라 해시 전에 전부 걷어낸다.
     *
     * 쿠키에 담는 값도 반드시 이걸 거쳐야 한다 — 사용자가 친 원문을 그대로 넣으면 공백이 섞였을 때
     * 쿠키 규격(RFC2616) 위반으로 400이 난다(2026-08-20 발견).
     */
    public String normalizeKey(String licenseKey) {
        if (licenseKey == null) {
            return "";
        }
        return licenseKey.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }
}
