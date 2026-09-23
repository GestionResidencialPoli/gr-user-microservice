package com.uni.usermicroservice.security;

import jakarta.validation.constraints.NotBlank;

public record SsoExchangeRequest(@NotBlank String code) {
}
