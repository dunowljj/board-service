package com.dunowljj.board.common.error;

import java.util.Map;

public class InvalidUserContentException extends BusinessException {

    public InvalidUserContentException(String field) {
        super(ErrorCode.INVALID_USER_CONTENT, Map.of("field", field));
    }
}
