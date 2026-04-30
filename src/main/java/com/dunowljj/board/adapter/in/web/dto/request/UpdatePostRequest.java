package com.dunowljj.board.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdatePostRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull @Size(max = 10000) String body) {}
