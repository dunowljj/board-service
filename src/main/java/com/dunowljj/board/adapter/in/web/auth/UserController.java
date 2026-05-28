package com.dunowljj.board.adapter.in.web.auth;

import com.dunowljj.board.adapter.in.web.dto.response.UserResponse;
import com.dunowljj.board.application.port.in.GetCurrentUserUseCase;
import com.dunowljj.board.application.port.in.result.UserResult;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final GetCurrentUserUseCase getCurrentUserUseCase;

    public UserController(GetCurrentUserUseCase getCurrentUserUseCase) {
        this.getCurrentUserUseCase = getCurrentUserUseCase;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal Long actorUserId) {
        UserResult result = getCurrentUserUseCase.getById(actorUserId);
        return ResponseEntity.ok(UserResponse.from(result));
    }
}
