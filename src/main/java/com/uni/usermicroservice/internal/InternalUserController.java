package com.uni.usermicroservice.internal;

import com.uni.usermicroservice.identity.domain.User;
import com.uni.usermicroservice.identity.domain.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/users")
public class InternalUserController {

    private final UserRepository userRepository;

    public InternalUserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<InternalUserResponse> getById(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(InternalUserController::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static InternalUserResponse toResponse(User user) {
        return new InternalUserResponse(user.getId(), user.getFirstName(), user.getLastName());
    }
}
