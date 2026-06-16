package com.investmenttracker.auth;

import com.investmenttracker.user.User;
import com.investmenttracker.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based test for User Resolution Consistency.
 *
 * <p><b>Property 1: User Resolution Consistency</b></p>
 *
 * <p><b>Validates: Requirements 3.4, 7.3</b></p>
 *
 * <p>For any valid JWT with a {@code sub} claim that maps to an existing user in the
 * database, the user resolution logic SHALL always return the same user record regardless
 * of how many times it's called.</p>
 */
class UserResolutionConsistencyPropertyTest {

    private UserRepository userRepository;
    private UserResolutionFilter filter;

    // In-memory store for users keyed by cognitoSub
    private Map<String, User> userStore;
    private AtomicLong idGenerator;

    @BeforeProperty
    void setUp() {
        userStore = new HashMap<>();
        idGenerator = new AtomicLong(1);

        userRepository = mock(UserRepository.class);

        // Mock findByCognitoSub: look up in store
        when(userRepository.findByCognitoSub(any())).thenAnswer(invocation -> {
            String sub = invocation.getArgument(0);
            return Optional.ofNullable(userStore.get(sub));
        });

        // Mock save: assign id, store in map, return
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId(idGenerator.getAndIncrement());
            }
            userStore.put(user.getCognitoSub(), user);
            return user;
        });

        filter = new UserResolutionFilter(userRepository);
    }

    @Property(tries = 100)
    @Label("User resolution always returns the same user for the same sub claim")
    void resolutionAlwaysReturnsSameUserForSameSub(
            @ForAll("validCognitoSubs") String cognitoSub,
            @ForAll("validUsernames") String username,
            @ForAll("validEmails") String email
    ) throws Exception {
        // Pre-provision the user in the store (simulating an existing user)
        User existingUser = new User();
        existingUser.setId(idGenerator.getAndIncrement());
        existingUser.setCognitoSub(cognitoSub);
        existingUser.setUsername(username);
        existingUser.setEmail(email);
        userStore.put(cognitoSub, existingUser);

        // Create a JWT with the given sub claim
        Jwt jwt = buildJwt(cognitoSub, username, email);
        JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(jwt);

        // Set up security context
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(jwtAuth);
        SecurityContextHolder.setContext(securityContext);

        // Invoke the filter multiple times and capture the resolved user each time
        User[] resolvedUsers = new User[3];
        for (int i = 0; i < 3; i++) {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            FilterChain filterChain = mock(FilterChain.class);

            // Capture the user set as request attribute
            final int index = i;
            doAnswer(inv -> {
                resolvedUsers[index] = (User) inv.getArgument(1);
                return null;
            }).when(request).setAttribute(eq("authenticatedUser"), any(User.class));

            filter.doFilterInternal(request, response, filterChain);

            // Verify filter chain was invoked
            verify(filterChain).doFilter(request, response);
        }

        // All resolutions should return the same user (same id and same cognitoSub)
        assertThat(resolvedUsers[0]).isNotNull();
        assertThat(resolvedUsers[1]).isNotNull();
        assertThat(resolvedUsers[2]).isNotNull();

        assertThat(resolvedUsers[0].getId()).isEqualTo(resolvedUsers[1].getId());
        assertThat(resolvedUsers[1].getId()).isEqualTo(resolvedUsers[2].getId());

        assertThat(resolvedUsers[0].getCognitoSub()).isEqualTo(cognitoSub);
        assertThat(resolvedUsers[1].getCognitoSub()).isEqualTo(cognitoSub);
        assertThat(resolvedUsers[2].getCognitoSub()).isEqualTo(cognitoSub);

        // Cleanup security context
        SecurityContextHolder.clearContext();
    }

    // --- Helper ---

    private Jwt buildJwt(String sub, String username, String email) {
        Map<String, Object> headers = Map.of("alg", "RS256", "typ", "JWT");
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", sub);
        claims.put("cognito:username", username);
        claims.put("email", email);
        claims.put("iss", "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_test");

        return new Jwt(
                "mock-token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                headers,
                claims
        );
    }

    // --- Custom Arbitraries ---

    @Provide
    Arbitrary<String> validCognitoSubs() {
        // Cognito sub format: UUID-like string (e.g., "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        return Arbitraries.strings()
                .withCharRange('a', 'f')
                .withCharRange('0', '9')
                .withChars('-')
                .ofMinLength(10)
                .ofMaxLength(36)
                .filter(s -> !s.startsWith("-") && !s.endsWith("-") && !s.contains("--"));
    }

    @Provide
    Arbitrary<String> validUsernames() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('_', '-')
                .ofMinLength(3)
                .ofMaxLength(50);
    }

    @Provide
    Arbitrary<String> validEmails() {
        Arbitrary<String> localPart = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .withChars('.', '_')
                .ofMinLength(1)
                .ofMaxLength(20)
                .filter(s -> !s.startsWith(".") && !s.endsWith("."));

        Arbitrary<String> domain = Arbitraries.of("example.com", "test.org", "mail.io", "company.co");

        return Combinators.combine(localPart, domain).as((local, dom) -> local + "@" + dom);
    }
}
