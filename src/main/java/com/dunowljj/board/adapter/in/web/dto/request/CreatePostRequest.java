package com.dunowljj.board.adapter.in.web.dto.request;

import com.dunowljj.board.domain.post.PostContent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 게시글 작성 요청. author 필드는 *인증 주체에서 자동 도출* 되므로 입력 차단 (PLAN-0011 §8).
 *
 * <p>길이 한도는 도메인 {@link PostContent#MAX_TITLE_LENGTH}/{@link PostContent#MAX_BODY_LENGTH} 를
 * 단일 출처로 참조한다(ADR-0005 §5.1 "Post 적용"). `@Size` 의 `max` 와 메시지 숫자 모두 상수에서 파생 —
 * 리터럴 하드코딩 금지(상수 한 곳만 바꾸면 검증·메시지 동시 반영). `errors[].reason` 이 사용자 표시
 * 메시지이므로 모든 제약에 한국어 메시지를 명시(영문 기본 누출 차단). 프로그램적 분기는
 * `errors[].code` 담당 — 제약 타입에서 핸들러가 파생하므로 여기 적지 않는다 (ADR-0005 §5.2).
 */
public record CreatePostRequest(
        @NotBlank(message = "제목을 입력해주세요")
        @Size(max = PostContent.MAX_TITLE_LENGTH,
                message = "제목은 " + PostContent.MAX_TITLE_LENGTH + "자 이하여야 합니다")
        String title,

        @NotNull(message = "본문을 입력해주세요")
        @Size(max = PostContent.MAX_BODY_LENGTH,
                message = "본문은 " + PostContent.MAX_BODY_LENGTH + "자 이하여야 합니다")
        String body) {}
