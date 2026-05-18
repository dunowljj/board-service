package com.dunowljj.board.application.port.in.result;

import java.util.List;

public record PostListResult(
        List<AuditedPostResult> posts,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
