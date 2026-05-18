package com.dunowljj.board.application.port.in;

import com.dunowljj.board.application.port.in.result.AuditedPostResult;

public interface GetPostUseCase {

    AuditedPostResult getById(Long id);
}
