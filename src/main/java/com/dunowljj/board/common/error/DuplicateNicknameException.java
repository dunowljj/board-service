package com.dunowljj.board.common.error;

import java.util.Map;

public class DuplicateNicknameException extends BusinessException {

    public DuplicateNicknameException(String nickname) {
        super(ErrorCode.DUPLICATE_NICKNAME, Map.of("nickname", nickname));
    }
}
