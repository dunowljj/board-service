package com.dunowljj.board.common.error;

import java.util.Map;

public class DuplicateEmailException extends BusinessException {

    public DuplicateEmailException(String email) {
        super(ErrorCode.DUPLICATE_EMAIL, Map.of("email", email));
    }
}
