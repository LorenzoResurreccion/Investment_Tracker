package com.investmenttracker.auth;

import com.investmenttracker.user.User;
import com.investmenttracker.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based test for Auto-Provisioning Correctness.
 *
 * <p><b>Property 2: Auto-Provisioning Correctness</b></p>
 *
 * <p><b>Validates: Requirements 3.5</b></p>
 *
 * <p>For any valid JWT containing a {@code sub} claim not present in the users table,
 * the system SHALL create exactly one new user record with {@code cognito_sub},
 * {@code username}, and {@code email} matching the JWT claims, and subsequent requests
 * with the same {@code sub} SHALL resolve to that same user.</p>
 */
class AutoProvisioningCorrectnessPropertyTest {

    private UserRepository userRepository;
    private UserResolutionFilter filter;

    // In-memory store for users keyed by cognitoSub
    private Map<String, User> userStore;
    private AtomicLong idGenerator;
    private AtomicInteger saveCallCount;

    @BeforeTry
    void setUp() {
        userStore = new HashMap<>();
        idGenerator = new AtomicLong(1);
        saveCallCount = new AtomicInteger(0);

        userRepository = mock(UserRepository.class);

        // Mock findByCognitoSub: look up in store
        when(userRepository.findByCognitoSub(any())).thenAnswer(invocation -> {
            String sub = invocation.getArgument(0);
            return Optional.ofNullable(userStore.get(sub));
        });

        // Mock save: assign id, store in map, track call count, return
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId(idGenerator.getAndIncrement());
            }
            userStore.put(user.getCognitoSub(), user);
            saveCallCount.incrementAndGet();
            return user;
        });

        filter = new UserResolutionFilter(userRepository);
    }

    @Property(tries = 100)
    @Label("Auto-provisioning creates a user with matching claims for new sub")
    void autoProvisioningCreatesUserWithMatchingClaims(
            @ForAll("validCognitoSubs") String cognitoSub,
            @ForAll("validUsernames") String username,
            @ForAll("validEmails") String email
    ) throws Exception {
        // Create a JWT with the given sub claim (new user, not in DB)
        Jwt jwt = buildJwt(cognitoSub, username, email);
        JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(jwt);

        // Set up security context
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(jwtAuth);
        SecurityContextHolder.setContext(securityContext);

        // Invoke the filter — should auto-provision the user
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        // Capture the user set as request attribute
        User[] resolvedUser = new User[1];
        doAnswer(inv -> {
            resolvedUser[0] = (User) inv.getArgument(1);
            return null;
        }).when(request).setAttribute(eq("authenticatedUser"), any(User.class));

        filter.doFilterInternal(request, response, filterChain);

        // Verify the filter chain was invoked
        verify(filterChain).doFilter(request, response);

        // Verify a user was created with matching claims
        assertThat(resolvedUser[0]).isNotNull();
        assertThat(resolvedUser[0].getCognitoSub()).isEqualTo(cognitoSub);
        assertThat(resolvedUser[0].getUsername()).isEqualTo(username);
        assertThat(resolvedUser[0].getEmail()).isEqualTo(email);
        assertThat(resolvedUser[0].getId()).isNotNull();

        // Verify save was called exactly once (user was created)
        assertThat(saveCallCount.get()).isEqualTo(1);

        // Verify the user is now in the store
        assertThat(userStore).containsKey(cognitoSub);
        assertThat(userStore.get(cognitoSub).getCognitoSub()).isEqualTo(cognitoSub);

        // Cleanup security context
        SecurityContextHolder.clearContext();
    }

    @Property(tries = 100)
    @Label("Subsequent lookups after auto-provisioning resolve to the same user")
    void subsequentLookupsResolveSameUser(
            @ForAll("validCognitoSubs") String cognitoSub,
            @ForAll("validUsernames") String username,
            @ForAll("validEmails") String email
    ) throws Exception {
        // Create a JWT with the given sub claim
        Jwt jwt = buildJwt(cognitoSub, username, email);
        JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(jwt);

        // Set up security context
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(jwtAuth);
        SecurityContextHolder.setContext(securityContext);

        // First invocation — auto-provisions the user
        User[] firstResolved = new User[1];
        {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            FilterChain filterChain = mock(FilterChain.class);

            doAnswer(inv -> {
                firstResolved[0] = (User) inv.getArgument(1);
                return null;
            }).when(request).setAttribute(eq("authenticatedUser"), any(User.class));

            filter.doFilterInternal(request, response, filterChain);
        }

        // Second invocation — should resolve to the same user (no new creation)
        User[] secondResolved = new User[1];
        {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            FilterChain filterChain = mock(FilterChain.class);

            doAnswer(inv -> {
                secondResolved[0] = (User) inv.getArgument(1);
                return null;
            }).when(request).setAttribute(eq("authenticatedUser"), any(User.class));

            filter.doFilterInternal(request, response, filterChain);
        }

        // Third invocation — verify stability
        User[] thirdResolved = new User[1];
        {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            FilterChain filterChain = mock(FilterChain.class);

            doAnswer(inv -> {
                thirdResolved[0] = (User) inv.getArgument(1);
                return null;
            }).when(request).setAttribute(eq("authenticatedUser"), any(User.class));

            filter.doFilterInternal(request, response, filterChain);
        }

        // All resolutions should return the same user
        assertThat(firstResolved[0]).isNotNull();
        assertThat(secondResolved[0]).isNotNull();
        assertThat(thirdResolved[0]).isNotNull();

        assertThat(firstResolved[0].getId()).isEqualTo(secondResolved[0].getId());
        assertThat(secondResolved[0].getId()).isEqualTo(thirdResolved[0].getId());

        assertThat(firstResolved[0].getCognitoSub()).isEqualTo(cognitoSub);
        assertThat(secondResolved[0].getCognitoSub()).isEqualTo(cognitoSub);
        assertThat(thirdResolved[0].getCognitoSub()).isEqualTo(cognitoSub);

        // Verify save was called only once (only the first call created the user)
        assertThat(saveCallCount.get()).isEqualTo(1);

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
        // Cognito sub format: UUID-like string
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
