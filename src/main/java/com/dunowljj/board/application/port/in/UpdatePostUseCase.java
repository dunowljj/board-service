package com.dunowljj.board.application.port.in;

import com.dunowljj.board.application.port.in.result.AuditedPostResult;

public interface UpdatePostUseCase {

    AuditedPostResult update(UpdatePostCommand command);

    record UpdatePostCommand(Long id, String title, String body) {}
}
