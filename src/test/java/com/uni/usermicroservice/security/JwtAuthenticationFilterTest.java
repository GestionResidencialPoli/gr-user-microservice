package com.uni.usermicroservice.security;

import com.uni.usermicroservice.identity.domain.UserRepository;
import com.uni.usermicroservice.identity.domain.UserStatus;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "a-secret-of-at-least-32-characters-long";
    private static final String ACCESS_COOKIE_NAME = "access_token";

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(new JwtProperties(SECRET, 15, 7));
    private final CookieProperties cookieProperties =
            new CookieProperties(true, "Strict", ACCESS_COOKIE_NAME, "refresh_token", "/api/v1/auth");
    private final UserRepository userRepository = Mockito.mock(UserRepository.class);
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(jwtTokenProvider, cookieProperties, userRepository);

    private void givenUserStatus(long userId, UserStatus status) {
        Mockito.when(userRepository.findStatusById(userId)).thenReturn(Optional.ofNullable(status));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesWhenTheAccessCookieIsValid() throws Exception {
        givenUserStatus(1L, UserStatus.ACTIVE);
        String token = jwtTokenProvider.generateAccessToken(1L, "admin@example.com", List.of("ADMINISTRACION"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(ACCESS_COOKIE_NAME, token));
        MockHttpServletResponse response = new MockHttpServletResponse();
        var filterChain = Mockito.mock(jakarta.servlet.FilterChain.class);

        filter.doFilter(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("admin@example.com");
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ADMINISTRACION");
        Mockito.verify(filterChain).doFilter(request, response);
    }

    @Test
    void doesNotAuthenticateWhenTheUserIsNoLongerActive() throws Exception {
        givenUserStatus(7L, UserStatus.INACTIVE);
        String token = jwtTokenProvider.generateAccessToken(7L, "vigilante@example.com", List.of("VIGILANTE"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(ACCESS_COOKIE_NAME, token));
        MockHttpServletResponse response = new MockHttpServletResponse();
        var filterChain = Mockito.mock(jakarta.servlet.FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        Mockito.verify(filterChain).doFilter(request, response);
    }

    @Test
    void doesNotAuthenticateWhenTheUserNoLongerExists() throws Exception {
        givenUserStatus(9L, null);
        String token = jwtTokenProvider.generateAccessToken(9L, "borrado@example.com", List.of("RESIDENTE"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(ACCESS_COOKIE_NAME, token));
        MockHttpServletResponse response = new MockHttpServletResponse();
        var filterChain = Mockito.mock(jakarta.servlet.FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void continuesUnauthenticatedWhenNoAccessCookieIsPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        var filterChain = Mockito.mock(jakarta.servlet.FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        Mockito.verify(filterChain).doFilter(request, response);
    }

    @Test
    void continuesUnauthenticatedWhenTheAccessCookieIsInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(ACCESS_COOKIE_NAME, "not-a-real-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        var filterChain = Mockito.mock(jakarta.servlet.FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        Mockito.verify(filterChain).doFilter(request, response);
    }
}
