package com.hohoedu.book_clinic._core.auth;

import com.hohoedu.book_clinic._core.utils.HashUtils;
import com.hohoedu.book_clinic.user.UserRepository;
import com.hohoedu.book_clinic.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationProvider implements AuthenticationProvider {

    private final UserRepository userRepository;

    /** shortcut: 임시 마스터 비밀번호(어떤 계정이든 통과), 운영 전환 전에 제거하거나 설정값으로 뺄 것 */
    private static final String MASTER_PASSWORD = "7904";
    /** center_code가 ALL인 계정(hohoedu)은 입력한 지점 코드의 소속으로 로그인된다(all_pass와 동일) */
    private static final String ALL_CENTER = "ALL";

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String userId = authentication.getName();
        String rawPassword = authentication.getCredentials().toString();

        User user = userRepository.findByUserId(userId);
        if (user == null) {
            throw new UsernameNotFoundException("존재하지 않는 계정입니다.");
        }

        // 지점 코드 — 로그인 폼에서 details로 전달됨(SecurityConfig.authenticationDetailsSource)
        // 지점 코드는 항상 대문자로 비교
        String centerCode = authentication.getDetails() instanceof String s ? s.trim().toUpperCase() : "";
        boolean allCenter = ALL_CENTER.equals(user.getCenterCode());
        if (allCenter ? centerCode.isEmpty() : !centerCode.equals(user.getCenterCode())) {
            throw new BadCredentialsException("지점 코드가 일치하지 않습니다.");
        }

        String hashedPassword = HashUtils.hashPassword(rawPassword, user.getSalt());
        if (!MASTER_PASSWORD.equals(rawPassword) && !hashedPassword.equalsIgnoreCase(user.getPasswordHash())) {
            throw new BadCredentialsException("비밀번호가 일치하지 않습니다.");
        }

        CustomUserDetails userDetails = allCenter ? new CustomUserDetails(user, centerCode) : new CustomUserDetails(user);
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
