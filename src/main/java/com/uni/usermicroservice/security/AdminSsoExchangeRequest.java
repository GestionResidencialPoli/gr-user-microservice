package com.uni.usermicroservice.security;

import jakarta.validation.constraints.NotBlank;

public record AdminSsoExchangeRequest(@NotBlank String code) {
}
