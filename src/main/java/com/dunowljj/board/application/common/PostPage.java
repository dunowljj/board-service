package com.dunowljj.board.application.common;

import com.dunowljj.board.domain.post.Post;

import java.util.List;

public record PostPage(List<Post> items, long totalElements) {}
