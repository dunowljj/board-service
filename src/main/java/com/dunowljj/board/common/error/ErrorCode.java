package com.dunowljj.board.common.error;

/**
 * Catalog of every {@code code} value that may appear in the {@code code} field of an error
 * response body. <b>This is a response-contract catalog, not a "business failure" catalog</b>:
 * 4xx business codes and the 5xx fallback code ({@link #INTERNAL_ERROR}) coexist here so that
 * clients and tooling can enumerate every possible response code from one source of truth.
 * <p>
 * Throw rules (enforced by {@link BusinessException}):
 * <ul>
 *   <li>4xx codes are throwable via {@code BusinessException} subtypes (domain/use-case code).</li>
 *   <li>{@link #INTERNAL_ERROR} is <b>not throwable</b> via {@code BusinessException} —
 *       its constructor rejects {@link ErrorCategory#INTERNAL}.
 *       It is emitted only by the web-adapter 5xx fallback path in
 *       {@code GlobalExceptionHandler}.</li>
 * </ul>
 * See ADR-0005 §2.
 */
public enum ErrorCode {
    POST_NOT_FOUND("POST_NOT_FOUND", ErrorCategory.NOT_FOUND, "게시글을 찾을 수 없습니다"),
    INVALID_POST_CONTENT("INVALID_POST_CONTENT", ErrorCategory.INVALID_INPUT, "게시글 내용이 올바르지 않습니다"),
    /**
     * Generic fallback for Spring MVC framework client errors (malformed JSON,
     * path/query parameter type mismatch, missing required parameter, unsupported
     * media type, method not allowed, etc.). Emitted by
     * {@code GlobalExceptionHandler.handleExceptionInternal} when the parent
     * {@code ResponseEntityExceptionHandler} resolves a 4xx framework exception.
     * Not throwable via {@code BusinessException} subtypes (no domain code throws this).
     */
    MALFORMED_REQUEST("MALFORMED_REQUEST", ErrorCategory.INVALID_INPUT, "요청 형식이 올바르지 않습니다"),
    /**
     * Bean Validation failures from web-adapter input validation
     * ({@code @Valid @RequestBody}, {@code @RequestParam @Min}, etc.).
     * Emitted by {@code GlobalExceptionHandler}; not throwable via
     * {@code BusinessException} subtypes.
     */
    VALIDATION_FAILED("VALIDATION_FAILED", ErrorCategory.INVALID_INPUT, "입력 형식이 올바르지 않습니다"),
    ACCESS_DENIED("ACCESS_DENIED", ErrorCategory.FORBIDDEN, "접근 권한이 없습니다"),
    AUTHENTICATION_REQUIRED("AUTHENTICATION_REQUIRED", ErrorCategory.UNAUTHORIZED, "로그인이 필요합니다"),
    AUTHENTICATION_FAILED("AUTHENTICATION_FAILED", ErrorCategory.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다"),
    DUPLICATE_EMAIL("DUPLICATE_EMAIL", ErrorCategory.CONFLICT, "이미 사용 중인 이메일입니다"),
    DUPLICATE_NICKNAME("DUPLICATE_NICKNAME", ErrorCategory.CONFLICT, "이미 사용 중인 닉네임입니다"),
    USER_NOT_FOUND("USER_NOT_FOUND", ErrorCategory.NOT_FOUND, "사용자를 찾을 수 없습니다"),
    INVALID_USER_CONTENT("INVALID_USER_CONTENT", ErrorCategory.INVALID_INPUT, "사용자 정보가 올바르지 않습니다"),
    /**
     * Server-internal fallback. Reserved for the web-adapter 5xx fallback path in
     * {@code GlobalExceptionHandler}. Do <b>not</b> throw this via {@code BusinessException}
     * — the {@code BusinessException} constructor will reject it at runtime.
     */
    INTERNAL_ERROR("INTERNAL_ERROR", ErrorCategory.INTERNAL, "일시적인 오류가 발생했습니다");

    private final String code;
    private final ErrorCategory category;
    private final String defaultMessage;

    ErrorCode(String code, ErrorCategory category, String defaultMessage) {
        this.code = code;
        this.category = category;
        this.defaultMessage = defaultMessage;
    }

    public String code() {
        return code;
    }

    public ErrorCategory category() {
        return category;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
