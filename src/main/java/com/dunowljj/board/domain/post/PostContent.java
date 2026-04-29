package com.dunowljj.board.domain.post;

import com.dunowljj.board.common.error.InvalidPostContentException;
import java.util.Objects;

public class PostContent {

    private final String title;
    private final String body;

    public PostContent(String title, String body) {
        if (title == null || title.isBlank()) {
            throw new InvalidPostContentException("title");
        }
        if (body == null) {
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
        if (o == null || getClass() != o.getClass()) return false;
        PostContent that = (PostContent) o;
        return Objects.equals(title, that.title) && Objects.equals(body, that.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, body);
    }
}
