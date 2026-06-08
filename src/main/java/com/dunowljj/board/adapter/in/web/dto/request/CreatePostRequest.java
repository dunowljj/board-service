package com.dunowljj.board.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 게시글 작성 요청. author 필드는 *인증 주체에서 자동 도출* 되므로 입력 차단 (PLAN-0011 §8).
 */
public record CreatePostRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull @Size(max = 10000) String body) {}
