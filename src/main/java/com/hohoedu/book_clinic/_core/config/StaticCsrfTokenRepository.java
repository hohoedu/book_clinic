package com.hohoedu.book_clinic._core.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;

public class StaticCsrfTokenRepository implements CsrfTokenRepository {

    private static final String MASTER_TOKEN = "hohoedu-master-csrf-token";
    private static final String HEADER_NAME = "X-XSRF-TOKEN";
    private static final String PARAMETER_NAME = "_csrf";

    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        return new DefaultCsrfToken(HEADER_NAME, PARAMETER_NAME, MASTER_TOKEN);
    }

    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
    }

    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        return new DefaultCsrfToken(HEADER_NAME, PARAMETER_NAME, MASTER_TOKEN);
    }
}
