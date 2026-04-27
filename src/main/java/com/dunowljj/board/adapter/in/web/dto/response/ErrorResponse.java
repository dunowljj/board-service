package com.dunowljj.board.adapter.in.web.dto.response;

import com.dunowljj.board.common.error.ErrorCode;
import java.time.LocalDateTime;

public record ErrorResponse(String code, String message, LocalDateTime timestamp, String path) {

    public static ErrorResponse of(ErrorCode errorCode, String path) {
        return new ErrorResponse(errorCode.code(), errorCode.defaultMessage(), LocalDateTime.now(), path);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, String path) {
        return new ErrorResponse(errorCode.code(), message, LocalDateTime.now(), path);
    }
}
