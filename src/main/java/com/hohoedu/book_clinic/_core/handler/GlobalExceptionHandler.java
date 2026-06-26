package com.hohoedu.book_clinic._core.handler;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

    /** @Valid 검증 실패 시 필드명과 오류 메시지를 조합해 400 반환 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiUtils.ApiResult<?>> validationError(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return new ResponseEntity<>(ApiUtils.error(message, HttpStatus.BAD_REQUEST), HttpStatus.BAD_REQUEST);
    }

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

    /** 엑셀 파싱 등 IO 오류 — 400으로 처리 (잘못된 파일 형식이 대부분) */
    @ExceptionHandler(java.io.IOException.class)
    public ResponseEntity<ApiUtils.ApiResult<?>> ioError(java.io.IOException e) {
        return new ResponseEntity<>(ApiUtils.error("파일 처리 중 오류가 발생했습니다: " + e.getMessage(), HttpStatus.BAD_REQUEST), HttpStatus.BAD_REQUEST);
    }

    /** IllegalArgumentException — 엑셀 업로드 도중 비즈니스 검증 실패 시 400 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiUtils.ApiResult<?>> illegalArgument(IllegalArgumentException e) {
        return new ResponseEntity<>(ApiUtils.error(e.getMessage(), HttpStatus.BAD_REQUEST), HttpStatus.BAD_REQUEST);
    }
}
