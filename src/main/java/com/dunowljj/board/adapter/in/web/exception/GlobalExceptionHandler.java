package com.dunowljj.board.adapter.in.web.exception;

import com.dunowljj.board.adapter.in.web.dto.response.ErrorResponse;
import com.dunowljj.board.adapter.in.web.error.ErrorCategoryHttpStatusMapper;
import com.dunowljj.board.common.error.BusinessException;
import com.dunowljj.board.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex,
                                                        HttpServletRequest request) {
        HttpStatus status = ErrorCategoryHttpStatusMapper.toHttpStatus(ex.errorCode().category());
        ErrorResponse body = ErrorResponse.of(ex.errorCode(), request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Hook into the parent {@link ResponseEntityExceptionHandler} pipeline so that all
     * Spring MVC framework client-error exceptions (malformed JSON, type mismatch,
     * missing parameter, unsupported media type, method not allowed, etc.) preserve
     * their framework-resolved status while their body is shaped to our
     * {@link ErrorResponse}.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        String path = (request instanceof ServletWebRequest swr)
                ? swr.getRequest().getRequestURI()
                : "";
        ErrorCode code = statusCode.is4xxClientError()
                ? ErrorCode.MALFORMED_REQUEST
                : ErrorCode.INTERNAL_ERROR;
        ErrorResponse errorBody = ErrorResponse.of(code, path);
        return new ResponseEntity<>(errorBody, headers, statusCode);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex,
                                                          HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(ErrorCode.INTERNAL_ERROR, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
