package com.dunowljj.board.application.port.out;

import com.dunowljj.board.application.common.PostPage;
import com.dunowljj.board.domain.post.Post;

import java.util.Optional;

public interface LoadPostPort {

    Optional<Post> findById(Long id);

    PostPage findPage(int page, int size);
}
