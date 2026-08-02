package com.dunowljj.board.adapter.in.web.dto.response;

import com.dunowljj.board.adapter.in.web.error.ValidationErrorCode;

/**
 * 검증 실패 응답의 {@code errors[]} 엔트리 (ADR-0005 §4·§5.2).
 *
 * <p>{@code field} = 위반 필드명, {@code code} = 실패 종류의 *안정적 머신 식별자*(프로그램 분기용),
 * {@code reason} = *사용자 표시용 한국어 문구*. **분기는 {@code code} 로, 표시는 {@code reason} 으로** —
 * {@code reason} 문구는 UX·i18n 사정으로 바뀔 수 있고 안정성을 약속하지 않는다.
 *
 * <p>같은 {@code field} 에 서로 다른 {@code code} 가 여러 건 나올 수 있다 — 예: 공백 200자 초과 제목은
 * {@code @NotBlank}({@code REQUIRED}) 와 {@code @Size}({@code TOO_LONG}) 를 동시 위반한다(ADR-0005 §5.1
 * aggregation 계약). 클라이언트는 "필드당 1건"을 가정하면 안 된다.
 */
public record ValidationError(String field, ValidationErrorCode code, String reason) {
}
