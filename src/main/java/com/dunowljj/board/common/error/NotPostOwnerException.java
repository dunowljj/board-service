package com.dunowljj.board.common.error;

import java.util.Map;

public class NotPostOwnerException extends BusinessException {

    public NotPostOwnerException(Long postId, Long actorUserId) {
        super(ErrorCode.ACCESS_DENIED, Map.of("postId", postId, "actorUserId", actorUserId));
    }
}
