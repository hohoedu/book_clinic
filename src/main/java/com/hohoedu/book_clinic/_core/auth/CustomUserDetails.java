package com.hohoedu.book_clinic._core.auth;

import com.hohoedu.book_clinic.user.model.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class CustomUserDetails implements UserDetails {

    private final LoginUser loginUser;

    public CustomUserDetails(User user) {
        this.loginUser = LoginUser.builder()
                .id(user.getId())
                .centerCode(user.getCenterCode())
                .roleKey(user.getRoleKey())
                .userId(user.getUserId())
                .userName(user.getUserName())
                .type(user.getType())
                .isHan(user.getIsHan())
                .isBook(user.getIsBook())
                .isClinic(user.getIsClinic())
                .build();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + loginUser.getRoleKey()));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return loginUser.getUserId();
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
