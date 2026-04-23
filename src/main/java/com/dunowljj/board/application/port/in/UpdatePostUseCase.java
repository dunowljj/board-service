package com.dunowljj.board.application.port.in;

import com.dunowljj.board.domain.post.Post;

public interface UpdatePostUseCase {

    Post update(UpdatePostCommand command);

    record UpdatePostCommand(Long id, String title, String body) {}
}
