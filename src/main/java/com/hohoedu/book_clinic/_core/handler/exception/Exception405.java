package com.hohoedu.book_clinic._core.handler.exception;

import org.springframework.http.HttpStatus;
import com.hohoedu.book_clinic._core.utils.ApiUtils;
import lombok.Getter;

//METHOD_NOT_ALLOWED (허용되지 않은 메서드)
@Getter
public class Exception405 extends RuntimeException {

    public Exception405(String message) {
        super(message);
    }

    public ApiUtils.ApiResult<?> body() {
        return ApiUtils.error(getMessage(), HttpStatus.METHOD_NOT_ALLOWED);
    }

    public HttpStatus status() {
        return HttpStatus.METHOD_NOT_ALLOWED;
    }

}
