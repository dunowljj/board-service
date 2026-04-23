package com.dunowljj.board.application.port.in;

import com.dunowljj.board.application.port.in.result.PostListResult;

public interface ListPostsUseCase {

    PostListResult list(int page, int size);
}
