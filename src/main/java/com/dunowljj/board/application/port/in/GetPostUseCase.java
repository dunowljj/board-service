package com.dunowljj.board.application.port.in;

import com.dunowljj.board.domain.post.Post;

public interface GetPostUseCase {

    Post getById(Long id);
}
