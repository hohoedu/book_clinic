package com.hohoedu.book_clinic._core.handler.exception;

import org.springframework.http.HttpStatus;
import com.hohoedu.book_clinic._core.utils.ApiUtils;
import lombok.Getter;

//BAD_REQUEST (잘못된 요청)
@Getter
public class Exception400 extends RuntimeException {

    public Exception400(String message) {
        super(message);
    }

    public ApiUtils.ApiResult<?> body() {
        return ApiUtils.error(getMessage(), HttpStatus.BAD_REQUEST);
    }

    public HttpStatus status() {
        return HttpStatus.BAD_REQUEST;
    }

}
