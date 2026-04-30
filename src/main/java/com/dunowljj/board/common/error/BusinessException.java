package com.dunowljj.board.common.error;

import java.util.Collections;
import java.util.Map;

/**
 * Abstract base for 4xx, client-fixable failures (input invalid, resource not found,
 * permission denied, etc.).
 * <p>
 * <b>4xx-only invariant:</b> a {@code BusinessException} must carry an
 * {@link ErrorCode} whose {@link ErrorCategory} is <i>not</i> {@link ErrorCategory#INTERNAL}.
 * The constructor rejects {@code INTERNAL}-category codes by throwing
 * {@link IllegalArgumentException}. This guards against accidentally throwing
 * {@code INTERNAL_ERROR} (which is reserved for the web-adapter 5xx fallback
 * path in {@code GlobalExceptionHandler}; see ADR-0005 §1, §2).
 * <p>
 * Do not extend this class for server-internal failures (DB outage, external API
 * timeout, etc.). Such failures must propagate as plain runtime exceptions and be
 * handled by the web-adapter 5xx fallback path, which alone is allowed to emit
 * {@link ErrorCode#INTERNAL_ERROR}.
 */
public abstract class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> context;

    protected BusinessException(ErrorCode errorCode, Map<String, Object> context) {
        super(errorCode.defaultMessage());
        if (errorCode.category() == ErrorCategory.INTERNAL) {
            throw new IllegalArgumentException(
                    "BusinessException must not carry INTERNAL category: " + errorCode.code());
        }
        this.errorCode = errorCode;
        this.context = (context == null)
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(context);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> context() {
        return context;
    }
}
