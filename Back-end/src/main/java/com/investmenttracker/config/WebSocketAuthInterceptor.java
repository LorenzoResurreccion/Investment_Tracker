package com.investmenttracker.config;

import com.investmenttracker.user.User;
import com.investmenttracker.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * Intercepts the WebSocket upgrade handshake to authenticate the client.
 *
 * Extracts the JWT from the {@code token} query parameter, validates it
 * using Spring Security's {@link JwtDecoder}, resolves the user from the
 * {@code sub} claim, and stores the {@link User} entity in the WebSocket
 * session attributes.
 *
 * Rejects the connection (returns {@code false}) if the token is missing,
 * invalid, or expired.
 *
 * Requirements: 7.1, 7.2, 7.3
 */
@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    private final JwtDecoder jwtDecoder;
    private final UserRepository userRepository;

    public WebSocketAuthInterceptor(JwtDecoder jwtDecoder, UserRepository userRepository) {
        this.jwtDecoder = jwtDecoder;
        this.userRepository = userRepository;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                    ServerHttpResponse response,
                                    WebSocketHandler wsHandler,
                                    Map<String, Object> attributes) {
        String token = extractTokenFromQuery(request.getURI());
        if (token == null) {
            log.warn("WebSocket handshake rejected: missing token query parameter");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            Jwt jwt = jwtDecoder.decode(token);
            String sub = jwt.getSubject();
            User user = userRepository.findByCognitoSub(sub)
                    .orElseGet(() -> autoProvision(jwt));

            attributes.put("user", user);
            return true;
        } catch (JwtException e) {
            log.warn("WebSocket handshake rejected: invalid or expired token — {}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                                ServerHttpResponse response,
                                WebSocketHandler wsHandler,
                                Exception exception) {
        // No post-handshake action needed
    }

    /**
     * Extracts the value of the {@code token} query parameter from the URI.
     *
     * @param uri the WebSocket upgrade request URI
     * @return the token string, or {@code null} if not present
     */
    private String extractTokenFromQuery(URI uri) {
        String tokenParam = UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams()
                .getFirst("token");
        return (tokenParam != null && !tokenParam.isBlank()) ? tokenParam : null;
    }

    /**
     * Auto-provisions a new user from JWT claims when no existing user is found
     * for the given {@code sub} claim. Mirrors the logic in {@code UserResolutionFilter}.
     *
     * @param jwt the validated JWT
     * @return the newly created and persisted user
     */
    private User autoProvision(Jwt jwt) {
        String sub = jwt.getSubject();
        String username = jwt.getClaimAsString("cognito:username");
        String email = jwt.getClaimAsString("email");

        User newUser = new User();
        newUser.setCognitoSub(sub);
        newUser.setUsername(username != null ? username : sub);
        newUser.setEmail(email != null ? email : sub + "@unknown");
        return userRepository.save(newUser);
    }
}
