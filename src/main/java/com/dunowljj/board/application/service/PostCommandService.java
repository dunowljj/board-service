package com.dunowljj.board.application.service;

import com.dunowljj.board.application.port.in.CreatePostUseCase;
import com.dunowljj.board.application.port.in.DeletePostUseCase;
import com.dunowljj.board.application.port.in.UpdatePostUseCase;
import com.dunowljj.board.application.port.in.result.AuditedPostResult;
import com.dunowljj.board.application.port.out.CreatePostPort;
import com.dunowljj.board.application.port.out.DeletePostPort;
import com.dunowljj.board.application.port.out.LoadPostPort;
import com.dunowljj.board.application.port.out.UpdatePostPort;
import com.dunowljj.board.application.port.out.result.AuditedPost;
import com.dunowljj.board.common.error.PostNotFoundException;
import com.dunowljj.board.domain.post.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class PostCommandService implements CreatePostUseCase, UpdatePostUseCase, DeletePostUseCase {

    private final LoadPostPort loadPostPort;
    private final CreatePostPort createPostPort;
    private final UpdatePostPort updatePostPort;
    private final DeletePostPort deletePostPort;

    @Override
    public AuditedPostResult create(CreatePostCommand command) {
        Post post = Post.create(command.title(), command.body(), command.author());
        AuditedPost saved = createPostPort.create(post);
        return AuditedPostResult.from(saved.post(), saved.createdAt(), saved.updatedAt());
    }

    @Override
    public AuditedPostResult update(UpdatePostCommand command) {
        AuditedPost loaded = loadPostPort.findById(command.id())
                .orElseThrow(() -> new PostNotFoundException(command.id()));
        Post post = loaded.post();
        post.updateContent(command.title(), command.body());
        AuditedPost saved = updatePostPort.update(post);
        return AuditedPostResult.from(saved.post(), saved.createdAt(), saved.updatedAt());
    }

    @Override
    public void delete(Long id) {
        if (deletePostPort.deleteById(id) == 0) {
            throw new PostNotFoundException(id);
        }
    }
}
