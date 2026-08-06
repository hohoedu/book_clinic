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

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String userId = authentication.getName();
        String rawPassword = authentication.getCredentials().toString();

        User user = userRepository.findByUserId(userId);
        if (user == null) {
            throw new UsernameNotFoundException("존재하지 않는 계정입니다.");
        }

        String hashedPassword = HashUtils.hashPassword(rawPassword, user.getSalt());
        if (!hashedPassword.equalsIgnoreCase(user.getPasswordHash())) {
            throw new BadCredentialsException("비밀번호가 일치하지 않습니다.");
        }

        CustomUserDetails userDetails = new CustomUserDetails(user);
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
