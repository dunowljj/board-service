package com.dunowljj.board.adapter.in.web.dto.response;

import com.dunowljj.board.application.port.in.result.PostListResult;

import java.util.List;

public record PostListResponse(
        List<PostResponse> posts,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static PostListResponse from(PostListResult result) {
        List<PostResponse> posts = result.posts().stream()
                .map(PostResponse::from)
                .toList();
        return new PostListResponse(
                posts,
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages()
        );
    }
}
