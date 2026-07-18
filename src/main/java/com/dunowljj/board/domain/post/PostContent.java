package com.dunowljj.board.domain.post;

import com.dunowljj.board.common.error.InvalidPostContentException;
import java.util.Objects;

public final class PostContent {

    /**
     * 제목/본문 길이 한도의 단일 출처 (ADR-0005 §5.1 "Post 적용"). 경계 DTO 의 {@code @Size(max = …)}
     * 가 이 상수를 참조하고, 아래 생성자가 같은 상수로 비-웹 백스톱을 강제한다 — Java {@code char}
     * 수({@code String.length()}) 기준, trim 안 함({@code @Size} 와 동일 semantics).
     */
    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_BODY_LENGTH = 10_000;

    private final String title;
    private final String body;

    public PostContent(String title, String body) {
        if (title == null || title.isBlank()) {
            throw new InvalidPostContentException("title");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new InvalidPostContentException("title");
        }
        if (body == null) {
            throw new InvalidPostContentException("body");
        }
        if (body.length() > MAX_BODY_LENGTH) {
            throw new InvalidPostContentException("body");
        }
        this.title = title;
        this.body = body;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PostContent that)) return false;
        return Objects.equals(title, that.title) && Objects.equals(body, that.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, body);
    }
}
