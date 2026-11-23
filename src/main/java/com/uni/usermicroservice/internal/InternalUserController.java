package com.uni.usermicroservice.internal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/users")
public class InternalUserController {

    private final InternalDirectoryService directoryService;

    public InternalUserController(InternalDirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<InternalUserResponse> getById(@PathVariable Long id) {
        return ResponseEntity.of(directoryService.findUser(id));
    }
}
