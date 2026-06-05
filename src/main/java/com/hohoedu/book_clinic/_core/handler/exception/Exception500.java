package com.hohoedu.book_clinic._core.handler.exception;

import org.springframework.http.HttpStatus;

import com.hohoedu.book_clinic._core.utils.ApiUtils;

public class Exception500 extends RuntimeException {

    public Exception500(String message) {
        super(message);
    }

    public ApiUtils.ApiResult<?> body() {
        return ApiUtils.error(getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public HttpStatus status() {
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
