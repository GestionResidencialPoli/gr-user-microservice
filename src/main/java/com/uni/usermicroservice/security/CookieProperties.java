package com.uni.usermicroservice.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.cookie")
public record CookieProperties(
        boolean secure,
        String sameSite,
        String accessTokenName,
        String refreshTokenName,
        String refreshTokenPath
) {
}
