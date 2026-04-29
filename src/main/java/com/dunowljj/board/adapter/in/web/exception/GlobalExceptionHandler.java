package com.dunowljj.board.adapter.in.web.exception;

import com.dunowljj.board.adapter.in.web.error.ErrorCategoryHttpStatusMapper;
import com.dunowljj.board.common.error.BusinessException;
import com.dunowljj.board.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::validationError)
                .toList();
        return validationFailed(errors, headers, path(request));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> validationError(fieldName(result), reason(error))))
                .toList();
        return validationFailed(errors, headers, path(request));
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
        String path = path(request);
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

    private static ResponseEntity<Object> validationFailed(
            List<Map<String, String>> errors, HttpHeaders headers, String path) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED.defaultMessage());
        pd.setProperty("errors", errors);
        enrich(pd, path, ErrorCode.VALIDATION_FAILED);
        return new ResponseEntity<>(pd, headers, HttpStatus.BAD_REQUEST);
    }

    private static ProblemDetail problemDetail(HttpStatus status, String detail, String path, ErrorCode errorCode) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        enrich(pd, path, errorCode);
        return pd;
    }

    private static Map<String, String> validationError(FieldError error) {
        return validationError(error.getField(), reason(error));
    }

    private static Map<String, String> validationError(String field, String reason) {
        return Map.of("field", field, "reason", reason);
    }

    private static String fieldName(ParameterValidationResult result) {
        MethodParameter parameter = result.getMethodParameter();
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null) {
            if (!requestParam.name().isBlank()) {
                return requestParam.name();
            }
            if (!requestParam.value().isBlank()) {
                return requestParam.value();
            }
        }

        String parameterName = parameter.getParameterName();
        if (parameterName != null && !parameterName.isBlank()) {
            return parameterName;
        }
        return "arg" + parameter.getParameterIndex();
    }

    private static String reason(MessageSourceResolvable error) {
        String defaultMessage = error.getDefaultMessage();
        if (defaultMessage != null && !defaultMessage.isBlank()) {
            return defaultMessage;
        }

        String[] codes = error.getCodes();
        if (codes != null && codes.length > 0) {
            return codes[0];
        }
        return "validation failed";
    }

    private static String path(WebRequest request) {
        return (request instanceof ServletWebRequest swr)
                ? swr.getRequest().getRequestURI()
                : "";
    }

    private static void enrich(ProblemDetail pd, String path, ErrorCode errorCode) {
        if (path != null && !path.isEmpty()) {
            pd.setInstance(URI.create(path));
        }
        pd.setProperty("code", errorCode.code());
        pd.setProperty("timestamp", LocalDateTime.now().toString());
    }
}
