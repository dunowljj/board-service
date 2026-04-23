package com.dunowljj.board.application.port.in;

import com.dunowljj.board.domain.post.Post;

public interface CreatePostUseCase {

    Post create(CreatePostCommand command);

    record CreatePostCommand(String title, String body, String author) {}
}
