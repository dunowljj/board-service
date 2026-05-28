package com.dunowljj.board.common.error;

import java.util.Map;

public class UserNotFoundException extends BusinessException {

    public UserNotFoundException(Long userId) {
        super(ErrorCode.USER_NOT_FOUND, Map.of("userId", userId));
    }
}
