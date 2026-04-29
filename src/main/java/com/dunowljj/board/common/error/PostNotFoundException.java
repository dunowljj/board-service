package com.dunowljj.board.common.error;

import java.util.Map;

public class PostNotFoundException extends BusinessException {

    public PostNotFoundException(Long postId) {
        super(ErrorCode.POST_NOT_FOUND, Map.of("postId", postId));
    }
}
