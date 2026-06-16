package com.investmenttracker.auth;

import com.investmenttracker.user.User;
import com.investmenttracker.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that runs after Spring Security's JWT validation to resolve
 * the authenticated user from the JWT claims.
 *
 * Extracts the {@code sub}, {@code cognito:username}, and {@code email}
 * claims from the JWT, looks up the user by {@code cognito_sub}, and
 * auto-provisions a new user record if one does not exist.
 *
 * The resolved {@link User} entity is stored as a request attribute
 * ({@code authenticatedUser}) for downstream controllers to access.
 *
 * Requirements: 3.4, 3.5
 */
@Component
public class UserResolutionFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public UserResolutionFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            String sub = jwt.getSubject();
            String username = jwt.getClaimAsString("cognito:username");
            String email = jwt.getClaimAsString("email");

            User user = userRepository.findByCognitoSub(sub)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setCognitoSub(sub);
                    newUser.setUsername(username != null ? username : sub);
                    newUser.setEmail(email != null ? email : sub + "@unknown");
                    return userRepository.save(newUser);
                });

            request.setAttribute("authenticatedUser", user);
        }

        filterChain.doFilter(request, response);
    }
}
