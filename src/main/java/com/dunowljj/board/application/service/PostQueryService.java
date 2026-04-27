package com.dunowljj.board.application.service;

import com.dunowljj.board.application.port.in.GetPostUseCase;
import com.dunowljj.board.application.port.in.ListPostsUseCase;
import com.dunowljj.board.application.common.PostPage;
import com.dunowljj.board.application.port.in.result.PostListResult;
import com.dunowljj.board.application.port.out.LoadPostPort;
import com.dunowljj.board.common.error.PostNotFoundException;
import com.dunowljj.board.domain.post.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PostQueryService implements GetPostUseCase, ListPostsUseCase {

    private final LoadPostPort loadPostPort;

    @Override
    public Post getById(Long id) {
        return loadPostPort.findById(id)
                .orElseThrow(() -> new PostNotFoundException(id));
    }

    @Override
    public PostListResult list(int page, int size) {
        PostPage postPage = loadPostPort.findPage(page, size);
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) postPage.totalElements() / size);
        return new PostListResult(postPage.items(), page, size, postPage.totalElements(), totalPages);
    }
}
