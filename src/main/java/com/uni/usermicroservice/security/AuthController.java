package com.uni.usermicroservice.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthTokenService authTokenService;
    private final CookieProperties cookieProperties;

    public AuthController(AuthTokenService authTokenService, CookieProperties cookieProperties) {
        this.authTokenService = authTokenService;
        this.cookieProperties = cookieProperties;
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(HttpServletRequest request) {
        return CookieUtils.readCookie(request, cookieProperties.refreshTokenName())
                .flatMap(authTokenService::rotate)
                .map(this::withTokenCookies)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        CookieUtils.readCookie(request, cookieProperties.refreshTokenName())
                .ifPresent(authTokenService::revoke);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, authTokenService.clearedAccessCookie().toString())
                .header(HttpHeaders.SET_COOKIE, authTokenService.clearedRefreshCookie().toString())
                .build();
    }

    private ResponseEntity<Void> withTokenCookies(AuthTokenService.IssuedTokens tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, tokens.accessCookie().toString())
                .header(HttpHeaders.SET_COOKIE, tokens.refreshCookie().toString())
                .build();
    }
}
