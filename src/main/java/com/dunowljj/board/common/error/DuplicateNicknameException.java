package com.dunowljj.board.common.error;

import java.util.Map;

public class DuplicateNicknameException extends BusinessException {

    // context 에는 field 명만 — nickname 원문은 사용자 입력이라 로그(ctx)에 남기지 않는다 (ADR-0005 §3).
    public DuplicateNicknameException() {
        super(ErrorCode.DUPLICATE_NICKNAME, Map.of("field", "nickname"));
    }
}
