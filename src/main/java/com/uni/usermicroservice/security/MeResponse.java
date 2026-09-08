package com.uni.usermicroservice.security;

import java.util.List;

public record MeResponse(Long id, String email, String firstName, String lastName, List<String> roles) {
}
