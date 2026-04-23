package com.dunowljj.board.application.port.out;

import com.dunowljj.board.domain.post.Post;

public interface SavePostPort {

    Post save(Post post);
}
