package com.dunowljj.board.application.service;

import com.dunowljj.board.application.port.in.CreatePostUseCase;
import com.dunowljj.board.application.port.in.DeletePostUseCase;
import com.dunowljj.board.application.port.in.UpdatePostUseCase;
import com.dunowljj.board.application.port.out.DeletePostPort;
import com.dunowljj.board.application.port.out.LoadPostPort;
import com.dunowljj.board.application.port.out.SavePostPort;
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
    private final SavePostPort savePostPort;
    private final DeletePostPort deletePostPort;

    @Override
    public Post create(CreatePostCommand command) {
        Post post = Post.create(command.title(), command.body(), command.author());
        return savePostPort.save(post);
    }

    @Override
    public Post update(UpdatePostCommand command) {
        Post post = loadPostPort.findById(command.id())
                .orElseThrow(() -> new PostNotFoundException(command.id()));
        post.updateContent(command.title(), command.body());
        return savePostPort.save(post);
    }

    @Override
    public void delete(Long id) {
        if (deletePostPort.deleteById(id) == 0) {
            throw new PostNotFoundException(id);
        }
    }
}
