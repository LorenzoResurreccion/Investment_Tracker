package com.investmenttracker.config;

import com.investmenttracker.auth.UserResolutionFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration for OAuth2 JWT resource server.
 *
 * - Stateless session management (no HTTP session)
 * - CSRF disabled (stateless API)
 * - CORS enabled at the security filter level (handles preflight OPTIONS)
 * - /actuator/health and /ws/** permitted without authentication
 * - /api/** requires a valid JWT
 * - JWT resource server configured with Cognito issuer URI (via application.properties)
 * - UserResolutionFilter runs after JWT validation to resolve the authenticated user
 *
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserResolutionFilter userResolutionFilter;

    @Value("${app.frontend-origin:http://localhost:5173}")
    private String frontendOrigin;

    public SecurityConfig(UserResolutionFilter userResolutionFilter) {
        this.userResolutionFilter = userResolutionFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .bearerTokenResolver(bearerTokenResolver())
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter()))
            )
            .addFilterAfter(userResolutionFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(frontendOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "Authorization"));
        config.setExposedHeaders(List.of("Authorization", "Retry-After"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private JwtAuthenticationConverter jwtAuthConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("sub");
        return converter;
    }

    /**
     * Custom BearerTokenResolver that first checks the standard Authorization header,
     * then falls back to a "token" query parameter. This supports browser-based
     * downloads (window.open) where headers cannot be set.
     */
    private BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver defaultResolver = new DefaultBearerTokenResolver();
        return (HttpServletRequest request) -> {
            // First try the standard Authorization header
            String token = defaultResolver.resolve(request);
            if (token != null) {
                return token;
            }
            // Fall back to "token" query parameter (for CSV export via window.open)
            return request.getParameter("token");
        };
    }
}
