package com.dunowljj.board.adapter.in.web.exception;

import com.dunowljj.board.adapter.in.web.error.ErrorCategoryHttpStatusMapper;
import com.dunowljj.board.common.error.BusinessException;
import com.dunowljj.board.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex,
                                                        HttpServletRequest request) {
        HttpStatus status = ErrorCategoryHttpStatusMapper.toHttpStatus(ex.errorCode().category());
        ProblemDetail body = problemDetail(status, ex.errorCode().defaultMessage(),
                request.getRequestURI(), ex.errorCode());
        ResponseEntity<ProblemDetail> response = ResponseEntity.status(status).body(body);
        logBusiness(ex);
        return response;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::validationError)
                .toList();
        ResponseEntity<Object> response = validationFailed(errors, headers, status, path(request));
        logValidationFailed(errors);
        return response;
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        // Return-value validation failure (Spring sets status=500) is a server-side bug,
        // not a client input error. Respond as INTERNAL_ERROR directly. VALIDATION_FAILED
        // is reserved for client-input (request parameter / @RequestBody) violations only.
        if (ex.isForReturnValue()) {
            ProblemDetail body = problemDetail(status,
                    ErrorCode.INTERNAL_ERROR.defaultMessage(),
                    path(request), ErrorCode.INTERNAL_ERROR);
            ResponseEntity<Object> response = new ResponseEntity<>(body, headers, status);
            logInternalError("return-value validation failed", ex);
            return response;
        }
        List<Map<String, String>> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> validationError(fieldName(result), reason(error))))
                .toList();
        ResponseEntity<Object> response = validationFailed(errors, headers, status, path(request));
        logValidationFailed(errors);
        return response;
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
        if (statusCode.is4xxClientError()) {
            logMalformedRequest(ex);
        } else {
            logInternalError("framework exception", ex);
        }
        return new ResponseEntity<>(pd, headers, statusCode);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex,
                                                          HttpServletRequest request) {
        ProblemDetail body = problemDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_ERROR.defaultMessage(),
                request.getRequestURI(), ErrorCode.INTERNAL_ERROR);
        ResponseEntity<ProblemDetail> response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        logInternalError("unexpected exception", ex);
        return response;
    }

    private static ResponseEntity<Object> validationFailed(
            List<Map<String, String>> errors, HttpHeaders headers,
            HttpStatusCode statusCode, String path) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(statusCode,
                ErrorCode.VALIDATION_FAILED.defaultMessage());
        pd.setProperty("errors", errors);
        enrich(pd, path, ErrorCode.VALIDATION_FAILED);
        return new ResponseEntity<>(pd, headers, statusCode);
    }

    private static ProblemDetail problemDetail(HttpStatusCode status, String detail, String path, ErrorCode errorCode) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        enrich(pd, path, errorCode);
        return pd;
    }

    private static void logBusiness(BusinessException ex) {
        log.atWarn()
                .addKeyValue("code", ex.errorCode().code())
                .addKeyValue("ctx", ex.context())
                .log("business exception");
    }

    private static void logValidationFailed(List<Map<String, String>> errors) {
        log.atWarn()
                .addKeyValue("code", ErrorCode.VALIDATION_FAILED.code())
                .addKeyValue("errors", errors)
                .log("validation failed");
    }

    private static void logMalformedRequest(Exception ex) {
        log.atWarn()
                .addKeyValue("code", ErrorCode.MALFORMED_REQUEST.code())
                .addKeyValue("exceptionClass", ex.getClass().getSimpleName())
                .log("malformed request");
    }

    private static void logInternalError(String message, Exception ex) {
        log.atError()
                .addKeyValue("code", ErrorCode.INTERNAL_ERROR.code())
                .addKeyValue("exceptionClass", ex.getClass().getSimpleName())
                .setCause(ex)
                .log(message);
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
