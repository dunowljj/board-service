package com.dunowljj.board.adapter.in.web.error;

import com.dunowljj.board.common.error.ErrorCategory;
import org.springframework.http.HttpStatus;

public final class ErrorCategoryHttpStatusMapper {

    private ErrorCategoryHttpStatusMapper() {
    }

    public static HttpStatus toHttpStatus(ErrorCategory category) {
        return switch (category) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
