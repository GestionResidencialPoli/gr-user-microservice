package com.uni.usermicroservice.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, long accessTokenExpirationMinutes, long refreshTokenExpirationDays) {
}
