package com.dunowljj.board.adapter.in.web.exception;

import com.dunowljj.board.adapter.in.web.error.ErrorCategoryHttpStatusMapper;
import com.dunowljj.board.common.error.BusinessException;
import com.dunowljj.board.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex,
                                                        HttpServletRequest request) {
        HttpStatus status = ErrorCategoryHttpStatusMapper.toHttpStatus(ex.errorCode().category());
        ProblemDetail body = problemDetail(status, ex.errorCode().defaultMessage(),
                request.getRequestURI(), ex.errorCode());
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Hook into the parent {@link ResponseEntityExceptionHandler} pipeline so that all
     * Spring MVC framework client-error exceptions (malformed JSON, type mismatch,
     * missing parameter, unsupported media type, method not allowed, etc.) preserve
     * their framework-resolved status. The parent already produces a
     * {@link ProblemDetail} body (Spring 6 default); we enrich it with our
     * {@code code} / {@code timestamp} custom properties.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        String path = (request instanceof ServletWebRequest swr)
                ? swr.getRequest().getRequestURI()
                : "";
        ProblemDetail pd = (body instanceof ProblemDetail existing)
                ? existing
                : ProblemDetail.forStatus(statusCode);
        ErrorCode errorCode = statusCode.is4xxClientError()
                ? ErrorCode.MALFORMED_REQUEST
                : ErrorCode.INTERNAL_ERROR;
        enrich(pd, path, errorCode);
        return new ResponseEntity<>(pd, headers, statusCode);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex,
                                                          HttpServletRequest request) {
        ProblemDetail body = problemDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_ERROR.defaultMessage(),
                request.getRequestURI(), ErrorCode.INTERNAL_ERROR);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private static ProblemDetail problemDetail(HttpStatus status, String detail, String path, ErrorCode errorCode) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        enrich(pd, path, errorCode);
        return pd;
    }

    private static void enrich(ProblemDetail pd, String path, ErrorCode errorCode) {
        if (path != null && !path.isEmpty()) {
            pd.setInstance(URI.create(path));
        }
        pd.setProperty("code", errorCode.code());
        pd.setProperty("timestamp", LocalDateTime.now().toString());
    }
}
