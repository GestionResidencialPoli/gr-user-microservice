package com.uni.usermicroservice.internal;

import com.uni.usermicroservice.identity.domain.ResidencyTypeService;
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
    private final ResidencyTypeService residencyTypeService;

    public InternalUserController(UserRepository userRepository, ResidencyTypeService residencyTypeService) {
        this.userRepository = userRepository;
        this.residencyTypeService = residencyTypeService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<InternalUserResponse> getById(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private InternalUserResponse toResponse(User user) {
        InternalUserApartmentResponse apartment = residencyTypeService.residencyOf(user.getId())
                .map(InternalUserApartmentResponse::from)
                .orElse(null);
        return new InternalUserResponse(user.getId(), user.getFirstName(), user.getLastName(), apartment);
    }
}
