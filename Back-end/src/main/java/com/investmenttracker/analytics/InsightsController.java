package com.investmenttracker.analytics;

import com.investmenttracker.user.User;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for AI-powered portfolio insights.
 *
 * Mapped to /api/analytics/insights. Delegates insight generation to
 * InsightsService, enforcing per-user rate limiting (cooldown) and
 * handling Bedrock invocation errors.
 *
 * Requirements: 5.7, 9.1
 */
@RestController
@RequestMapping("/api/analytics")
public class InsightsController {

    private static final Logger log = LoggerFactory.getLogger(InsightsController.class);

    private final InsightsService insightsService;

    public InsightsController(InsightsService insightsService) {
        this.insightsService = insightsService;
    }

    /**
     * Generates AI portfolio insights for the authenticated user.
     *
     * Checks per-user cooldown before invoking Bedrock. Returns 429 if on cooldown,
     * 502 if Bedrock invocation fails, or 200 with the structured insights response.
     *
     * @param request the HTTP request (contains authenticatedUser attribute)
     * @return 200 with InsightsResponse, 429 if rate-limited, or 502 on Bedrock error
     */
    @PostMapping("/insights")
    public ResponseEntity<?> generateInsights(HttpServletRequest request) {
        User user = (User) request.getAttribute("authenticatedUser");

        // Check cooldown
        if (insightsService.isOnCooldown(user.getId())) {
            long retryAfterSeconds = insightsService.getRemainingCooldownSeconds(user.getId());
            log.info("InsightsController: user='{}' is on cooldown, retryAfter={}s",
                    user.getUsername(), retryAfterSeconds);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Retry-After", String.valueOf(retryAfterSeconds));

            Map<String, Object> body = Map.of(
                    "message", "Rate limit exceeded",
                    "retryAfterSeconds", retryAfterSeconds
            );

            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .headers(headers)
                    .body(body);
        }

        // Generate insights
        try {
            InsightsResponse response = insightsService.generateInsights(user);
            return ResponseEntity.ok(response);
        } catch (BedrockInvocationException e) {
            log.error("InsightsController: Bedrock invocation failed for user='{}'",
                    user.getUsername(), e);
            Map<String, String> errorBody = Map.of(
                    "message", "AI service unavailable"
            );
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorBody);
        }
    }
}
