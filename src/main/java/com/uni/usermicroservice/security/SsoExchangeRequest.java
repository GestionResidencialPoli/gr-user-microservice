package com.uni.usermicroservice.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SsoExchangeRequest(@NotBlank @Size(max = 200) String code) {
}
