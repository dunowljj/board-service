package com.dunowljj.board.adapter.in.web;

import com.dunowljj.board.adapter.in.web.dto.request.CreatePostRequest;
import com.dunowljj.board.adapter.in.web.dto.request.UpdatePostRequest;
import com.dunowljj.board.adapter.in.web.dto.response.PostListResponse;
import com.dunowljj.board.adapter.in.web.dto.response.PostResponse;
import com.dunowljj.board.application.port.in.*;
import com.dunowljj.board.application.port.in.result.PostListResult;
import com.dunowljj.board.domain.post.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final CreatePostUseCase createPostUseCase;
    private final GetPostUseCase getPostUseCase;
    private final UpdatePostUseCase updatePostUseCase;
    private final DeletePostUseCase deletePostUseCase;
    private final ListPostsUseCase listPostsUseCase;

    @PostMapping
    public ResponseEntity<PostResponse> create(@RequestBody CreatePostRequest request) {
        var command = new CreatePostUseCase.CreatePostCommand(
                request.title(), request.body(), request.author());
        Post post = createPostUseCase.create(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(PostResponse.from(post));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getById(@PathVariable Long id) {
        Post post = getPostUseCase.getById(id);
        return ResponseEntity.ok(PostResponse.from(post));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PostResponse> update(@PathVariable Long id,
                                               @RequestBody UpdatePostRequest request) {
        var command = new UpdatePostUseCase.UpdatePostCommand(
                id, request.title(), request.body());
        Post post = updatePostUseCase.update(command);
        return ResponseEntity.ok(PostResponse.from(post));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        deletePostUseCase.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<PostListResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PostListResult result = listPostsUseCase.list(page, size);
        return ResponseEntity.ok(PostListResponse.from(result));
    }
}
