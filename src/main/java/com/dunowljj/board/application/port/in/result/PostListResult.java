package com.dunowljj.board.application.port.in.result;

import com.dunowljj.board.domain.post.Post;

import java.util.List;

public record PostListResult(List<Post> posts, int page, int size, long totalElements, int totalPages) {}
