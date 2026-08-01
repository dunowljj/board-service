package com.dunowljj.board.domain.post;

import com.dunowljj.board.common.error.InvalidPostContentException;
import java.util.Objects;

public final class PostContent {

    /**
     * 제목/본문 길이 한도의 단일 출처 (ADR-0005 §5.1 "Post 적용"). 경계 DTO 의 {@code @Size(max = …)}
     * 가 이 상수를 참조하고, 아래 생성자가 같은 상수로 비-웹 백스톱을 강제한다 — Java {@code char}
     * 수({@code String.length()}) 기준, trim 안 함({@code @Size} 와 동일 semantics).
     *
     * <p><b>읽기 경로 커플링(의도적, PLAN-0012-B 코드리뷰 M1 수용):</b> 이 생성자는 {@code Post.create}
     * 뿐 아니라 영속 복원({@code PostMapper} → {@code Post.reconstitute})에서도 호출되므로, 길이 검사가
     * *DB 조회 시에도* 실행된다. DB 컬럼(title {@code VARCHAR(255)}, body {@code TEXT})은 이 한도보다
     * 큰 값을 담을 수 있으므로, 경계를 우회해 저장된 초과 행이나 *한도를 낮춘 뒤*의 기존 행은 조회 시
     * 예외가 된다. 현 데이터는 모두 경계({@code @Size})를 거쳤고 한도 인하는 별도 의제라 이 커플링을
     * 수용한다 — reconstitute 가 blank/null 을 이미 재검증하던 선재 동작의 확장. 한도 인하 시엔
     * 데이터 마이그레이션 또는 관대한 복원 경로를 별도로 검토한다.
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
