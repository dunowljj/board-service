package com.dunowljj.board.application.service;

import com.dunowljj.board.application.common.PostPage;
import com.dunowljj.board.application.port.in.GetPostUseCase;
import com.dunowljj.board.application.port.in.ListPostsUseCase;
import com.dunowljj.board.application.port.in.result.AuditedPostResult;
import com.dunowljj.board.application.port.in.result.PostListResult;
import com.dunowljj.board.application.port.out.LoadPostPort;
import com.dunowljj.board.application.port.out.result.AuditedPost;
import com.dunowljj.board.common.error.PostNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PostQueryService implements GetPostUseCase, ListPostsUseCase {

    private final LoadPostPort loadPostPort;

    @Override
    public AuditedPostResult getById(Long id) {
        AuditedPost loaded = loadPostPort.findById(id)
                .orElseThrow(() -> new PostNotFoundException(id));
        return AuditedPostResult.from(loaded.post(), loaded.createdAt(), loaded.updatedAt());
    }

    @Override
    public PostListResult list(int page, int size) {
        PostPage postPage = loadPostPort.findPage(page, size);
        List<AuditedPostResult> posts = postPage.items().stream()
                .map(audited -> AuditedPostResult.from(audited.post(), audited.createdAt(), audited.updatedAt()))
                .toList();
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) postPage.totalElements() / size);
        return new PostListResult(posts, page, size, postPage.totalElements(), totalPages);
    }
}
