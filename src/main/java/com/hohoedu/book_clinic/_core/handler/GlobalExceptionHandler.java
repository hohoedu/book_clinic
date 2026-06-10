package com.hohoedu.book_clinic._core.handler;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.hohoedu.book_clinic._core.handler.exception.Exception400;
import com.hohoedu.book_clinic._core.handler.exception.Exception401;
import com.hohoedu.book_clinic._core.handler.exception.Exception403;
import com.hohoedu.book_clinic._core.handler.exception.Exception404;
import com.hohoedu.book_clinic._core.handler.exception.Exception405;
import com.hohoedu.book_clinic._core.handler.exception.Exception500;
import com.hohoedu.book_clinic._core.utils.ApiUtils;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception400.class)
    public ResponseEntity<ApiUtils.ApiResult<?>> badRequest(Exception400 e) {
        return new ResponseEntity<>(e.body(), e.status());
    }

    @ExceptionHandler(Exception401.class)
    public ResponseEntity<ApiUtils.ApiResult<?>> unauthorized(Exception401 e) {
        return new ResponseEntity<>(e.body(), e.status());
    }

    @ExceptionHandler(Exception403.class)
    public ResponseEntity<ApiUtils.ApiResult<?>> forbidden(Exception403 e) {
        return new ResponseEntity<>(e.body(), e.status());
    }

    @ExceptionHandler(Exception404.class)
    public ResponseEntity<ApiUtils.ApiResult<?>> notFound(Exception404 e) {
        return new ResponseEntity<>(e.body(), e.status());
    }

    @ExceptionHandler(Exception405.class)
    public ResponseEntity<ApiUtils.ApiResult<?>> methodNotAllowed(Exception405 e) {
        return new ResponseEntity<>(e.body(), e.status());
    }

    @ExceptionHandler(Exception500.class)
    public ResponseEntity<ApiUtils.ApiResult<?>> internalServerError(Exception500 e) {
        return new ResponseEntity<>(e.body(), e.status());
    }
}
