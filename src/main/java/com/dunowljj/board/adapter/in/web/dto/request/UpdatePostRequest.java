package com.dunowljj.board.adapter.in.web.dto.request;

import com.dunowljj.board.domain.post.PostContent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 게시글 수정 요청. 길이 한도는 {@link CreatePostRequest} 와 동일하게 도메인
 * {@link PostContent#MAX_TITLE_LENGTH}/{@link PostContent#MAX_BODY_LENGTH} 를 단일 출처로 참조한다
 * (ADR-0005 §5.1 "Post 적용"). 모든 제약에 한국어 메시지 명시(`errors[].reason` 표시 계약).
 */
public record UpdatePostRequest(
        @NotBlank(message = "제목을 입력해주세요")
        @Size(max = PostContent.MAX_TITLE_LENGTH,
                message = "제목은 " + PostContent.MAX_TITLE_LENGTH + "자 이하여야 합니다")
        String title,

        @NotNull(message = "본문을 입력해주세요")
        @Size(max = PostContent.MAX_BODY_LENGTH,
                message = "본문은 " + PostContent.MAX_BODY_LENGTH + "자 이하여야 합니다")
        String body) {}
