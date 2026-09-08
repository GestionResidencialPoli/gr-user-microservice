package com.uni.usermicroservice.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final ProfileService profileService;
    private final PasswordResetService passwordResetService;
    private final AuthTokenService authTokenService;
    private final CookieProperties cookieProperties;

    public AuthController(
            AuthenticationService authenticationService,
            ProfileService profileService,
            PasswordResetService passwordResetService,
            AuthTokenService authTokenService,
            CookieProperties cookieProperties
    ) {
        this.authenticationService = authenticationService;
        this.profileService = profileService;
        this.passwordResetService = passwordResetService;
        this.authTokenService = authTokenService;
        this.cookieProperties = cookieProperties;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiError> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authenticationService.login(request.email(), request.password())
                .map(tokens -> ResponseEntity.ok()
                        .header(HttpHeaders.SET_COOKIE, tokens.accessCookie().toString())
                        .header(HttpHeaders.SET_COOKIE, tokens.refreshCookie().toString())
                        .<ApiError>build())
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                        ApiError.of(
                                HttpStatus.UNAUTHORIZED.value(),
                                "Unauthorized",
                                "Correo o contrasena invalidos",
                                httpRequest.getRequestURI()
                        )
                ));
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Authentication authentication) {
        return profileService.me(authentication.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PatchMapping("/me")
    public ResponseEntity<MeResponse> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return profileService.updatePhone(authentication.getName(), request.phone())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PostMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        profileService.changePassword(authentication.getName(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmation request) {
        passwordResetService.confirmReset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
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
