package com.dunowljj.board.common.error;

import java.util.Map;

public class InvalidPostContentException extends BusinessException {

    public InvalidPostContentException(String field) {
        super(ErrorCode.INVALID_POST_CONTENT, Map.of("field", field));
    }
}
