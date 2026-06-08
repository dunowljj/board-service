package com.dunowljj.board.common.error;

import java.util.Collections;

public class AuthenticationFailedException extends BusinessException {

    public AuthenticationFailedException() {
        super(ErrorCode.AUTHENTICATION_FAILED, Collections.emptyMap());
    }
}
