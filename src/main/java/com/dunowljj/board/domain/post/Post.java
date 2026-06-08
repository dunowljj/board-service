package com.dunowljj.board.domain.post;

import com.dunowljj.board.common.error.InvalidPostContentException;

public class Post {

    private Long id;
    private PostContent content;
    private Long authorId;

    private Post(Long id, PostContent content, Long authorId) {
        this.id = id;
        this.content = content;
        this.authorId = authorId;
    }

    public static Post create(String title, String body, Long authorId) {
        validateAuthorId(authorId);
        return new Post(null, new PostContent(title, body), authorId);
    }

    public static Post reconstitute(Long id, String title, String body, Long authorId) {
        if (id == null) {
            throw new IllegalArgumentException("Id must not be null");
        }
        validateAuthorId(authorId);
        return new Post(id, new PostContent(title, body), authorId);
    }

    private static void validateAuthorId(Long authorId) {
        if (authorId == null) {
            throw new InvalidPostContentException("authorId");
        }
    }

    public void updateContent(String title, String body) {
        this.content = new PostContent(title, body);
    }

    public Long getId() {
        return id;
    }

    public PostContent getContent() {
        return content;
    }

    public String getTitle() {
        return content.getTitle();
    }

    public String getBody() {
        return content.getBody();
    }

    public Long getAuthorId() {
        return authorId;
    }
}
