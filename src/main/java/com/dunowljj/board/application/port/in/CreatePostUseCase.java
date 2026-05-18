package com.dunowljj.board.application.port.in;

import com.dunowljj.board.application.port.in.result.AuditedPostResult;

public interface CreatePostUseCase {

    AuditedPostResult create(CreatePostCommand command);

    record CreatePostCommand(String title, String body, String author) {}
}
