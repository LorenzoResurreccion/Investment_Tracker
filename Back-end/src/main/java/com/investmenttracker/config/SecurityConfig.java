package com.investmenttracker.config;

import com.investmenttracker.auth.UserResolutionFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for OAuth2 JWT resource server.
 *
 * - Stateless session management (no HTTP session)
 * - CSRF disabled (stateless API)
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

    public SecurityConfig(UserResolutionFilter userResolutionFilter) {
        this.userResolutionFilter = userResolutionFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter()))
            )
            .addFilterAfter(userResolutionFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("sub");
        return converter;
    }
}
