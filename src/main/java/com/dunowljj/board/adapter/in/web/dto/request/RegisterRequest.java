package com.dunowljj.board.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(min = 2, max = 20) String nickname,
        @NotBlank @Size(min = 8, max = 72) String password) {}
