package com.dunowljj.board.common.error;

import java.util.Map;

public class DuplicateEmailException extends BusinessException {

    // context 에는 field 명만 — email 원문은 PII 라 로그(ctx)에 남기지 않는다 (ADR-0005 §3).
    public DuplicateEmailException() {
        super(ErrorCode.DUPLICATE_EMAIL, Map.of("field", "email"));
    }
}
