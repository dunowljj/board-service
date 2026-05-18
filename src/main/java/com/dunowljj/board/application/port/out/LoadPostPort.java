package com.dunowljj.board.application.port.out;

import com.dunowljj.board.application.common.PostPage;
import com.dunowljj.board.application.port.out.result.AuditedPost;

import java.util.Optional;

public interface LoadPostPort {

    Optional<AuditedPost> findById(Long id);

    PostPage findPage(int page, int size);
}
