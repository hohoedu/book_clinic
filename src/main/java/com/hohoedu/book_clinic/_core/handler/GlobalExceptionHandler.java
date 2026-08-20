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

    /**
     * 요청 본문(JSON) 파싱/타입 불일치 — 위 IOException 핸들러보다 먼저 매칭돼야 한다(2026-08-20
     * 스트레스 테스트로 발견). Jackson의 타입 불일치 예외(MismatchedInputException 등)가
     * IOException 계열이라, 이 핸들러가 없으면 엑셀 업로드용 "파일 처리 중 오류" 메시지가
     * 일반 JSON 바디 오류에도 그대로 붙어 사용자에게 엉뚱한 안내가 나갔다.
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiUtils.ApiResult<?>> messageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException e) {
        return new ResponseEntity<>(ApiUtils.error("요청 형식이 올바르지 않습니다.", HttpStatus.BAD_REQUEST), HttpStatus.BAD_REQUEST);
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

    /**
     * 날짜 파라미터 파싱 실패 — 컨트롤러 곳곳(MonitorController/DiaryController/ReservationController 등
     * 9곳)이 {@code LocalDate.parse(date)}를 그대로 쓰고 있어, "2026-13-40"처럼 형식이 안 맞는 날짜가
     * 오면 컨트롤러 개수만큼 각자 500(+스택트레이스 노출)이 나고 있었다(2026-08-20 스트레스 테스트로
     * 발견). 각 호출부를 전부 try/catch로 감싸는 대신, 여기 하나로 한 번에 막는다.
     */
    @ExceptionHandler(java.time.format.DateTimeParseException.class)
    public ResponseEntity<ApiUtils.ApiResult<?>> dateTimeParseError(java.time.format.DateTimeParseException e) {
        return new ResponseEntity<>(ApiUtils.error("날짜 형식이 올바르지 않습니다. (예: 2026-08-20)", HttpStatus.BAD_REQUEST), HttpStatus.BAD_REQUEST);
    }

    /**
     * 필수 쿼리 파라미터 누락(2026-08-20 스트레스 테스트로 발견) — 상태코드 자체는 스프링 기본값도
     * 400이라 맞지만, 응답 바디가 앱 공통 포맷({success,response,error})이 아니라 스프링 기본
     * 에러 페이지(스택트레이스 포함)로 나가고 있었다. 프론트는 전부 error.message만 읽으므로
     * 이 경로를 타면 화면에 아무 메시지도 못 띄운다.
     */
    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<ApiUtils.ApiResult<?>> missingParam(org.springframework.web.bind.MissingServletRequestParameterException e) {
        return new ResponseEntity<>(ApiUtils.error(e.getParameterName() + " 값이 필요합니다.", HttpStatus.BAD_REQUEST), HttpStatus.BAD_REQUEST);
    }
}
