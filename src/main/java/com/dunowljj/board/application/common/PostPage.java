package com.dunowljj.board.application.common;

import com.dunowljj.board.application.port.out.result.AuditedPost;

import java.util.List;

public record PostPage(List<AuditedPost> items, long totalElements) {}
