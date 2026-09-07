package com.uni.usermicroservice.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtTokenProvider jwtTokenProvider;
    private final CookieProperties cookieProperties;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, CookieProperties cookieProperties) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.cookieProperties = cookieProperties;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        CookieUtils.readCookie(request, cookieProperties.accessTokenName())
                .ifPresent(token -> jwtTokenProvider.parseClaims(token)
                        .ifPresentOrElse(
                                claims -> authenticate(claims, request),
                                () -> log.warn(
                                        "Token de acceso invalido o manipulado en {} desde {}",
                                        request.getRequestURI(),
                                        request.getRemoteAddr()
                                )
                        ));

        filterChain.doFilter(request, response);
    }

    private void authenticate(Claims claims, HttpServletRequest request) {
        List<GrantedAuthority> authorities = jwtTokenProvider.rolesOf(claims).stream()
                .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role))
                .map(GrantedAuthority.class::cast)
                .toList();

        var authentication = new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
        authentication.setDetails(request.getRemoteAddr());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
