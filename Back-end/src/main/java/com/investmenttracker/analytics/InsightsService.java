package com.investmenttracker.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmenttracker.investment.HoldingService;
import com.investmenttracker.investment.PortfolioSummaryResponse;
import com.investmenttracker.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service that generates AI-powered portfolio insights using AWS Bedrock (Claude Haiku).
 *
 * Fetches the user's portfolio summary, constructs a prompt with holdings context,
 * calls BedrockRuntimeClient.invokeModel(), and parses the response into structured
 * sections (allocation, risk, suggestions).
 *
 * Enforces a per-user cooldown of 60 seconds between successful insight requests.
 *
 * Requirements: 5.2, 5.7, 9.1, 9.2
 */
@Service
public class InsightsService {

    private static final Logger log = LoggerFactory.getLogger(InsightsService.class);
    private static final long COOLDOWN_SECONDS = 60;

    private static final String SYSTEM_PROMPT = """
            You are a portfolio analyst. Given the user's holdings, provide a structured analysis \
            with exactly three sections. Output ONLY plain text with these exact section headers on their own lines:
            ALLOCATION:
            RISK:
            SUGGESTIONS:
            Do not use markdown formatting (no #, **, or bullet points). \
            Keep each section concise (2-4 sentences). Do not provide specific buy/sell recommendations or price targets.""";

    private final HoldingService holdingService;
    private final ObjectMapper objectMapper;
    private final Map<Long, Instant> cooldownMap = new ConcurrentHashMap<>();

    @Value("${app.bedrock.model-id}")
    private String modelId;

    @Value("${app.bedrock.region}")
    private String bedrockRegion;

    private BedrockRuntimeClient bedrockClient;

    @org.springframework.beans.factory.annotation.Autowired
    public InsightsService(HoldingService holdingService, ObjectMapper objectMapper) {
        this.holdingService = holdingService;
        this.objectMapper = objectMapper;
    }

    /**
     * Package-private constructor for testing — allows injecting a pre-built BedrockRuntimeClient.
     */
    InsightsService(HoldingService holdingService, ObjectMapper objectMapper,
                    BedrockRuntimeClient bedrockClient, String modelId) {
        this.holdingService = holdingService;
        this.objectMapper = objectMapper;
        this.bedrockClient = bedrockClient;
        this.modelId = modelId;
    }

    @PostConstruct
    void initClient() {
        if (this.bedrockClient == null) {
            this.bedrockClient = BedrockRuntimeClient.builder()
                    .region(Region.of(bedrockRegion))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .overrideConfiguration(config -> config
                            .apiCallTimeout(Duration.ofSeconds(30)))
                    .build();
            log.info("InsightsService: initialized BedrockRuntimeClient for region='{}', model='{}'",
                    bedrockRegion, modelId);
        }
    }

    /**
     * Generates AI portfolio insights for the given user.
     *
     * Fetches the user's portfolio summary, constructs a prompt, calls Bedrock,
     * and parses the response into structured sections. Records the request time
     * for cooldown enforcement.
     *
     * @param user the authenticated user
     * @return structured insights response with allocation, risk, and suggestions
     * @throws BedrockInvocationException if the Bedrock API call fails
     */
    public InsightsResponse generateInsights(User user) {
        List<PortfolioSummaryResponse> summary = holdingService.getPortfolioSummary(user);
        String userPrompt = buildUserPrompt(summary);

        String requestBody = buildRequestBody(userPrompt);

        log.debug("InsightsService: invoking Bedrock for user='{}' with {} holdings",
                user.getUsername(), summary.size());

        InvokeModelRequest request = InvokeModelRequest.builder()
                .modelId(modelId)
                .contentType("application/json")
                .accept("application/json")
                .body(SdkBytes.fromUtf8String(requestBody))
                .build();

        InvokeModelResponse response;
        try {
            response = bedrockClient.invokeModel(request);
        } catch (Exception e) {
            log.error("InsightsService: Bedrock invocation failed for user='{}'", user.getUsername(), e);
            throw new BedrockInvocationException("Bedrock invocation failed: " + e.getMessage(), e);
        }

        String responseText = extractResponseText(response);
        InsightsResponse insights = parseResponse(responseText);

        // Record successful request time for cooldown
        cooldownMap.put(user.getId(), Instant.now());

        log.info("InsightsService: generated insights for user='{}'", user.getUsername());
        return insights;
    }

    /**
     * Checks whether the given user is currently within their cooldown period.
     *
     * @param userId the user's ID
     * @return true if the user must wait before making another request
     */
    public boolean isOnCooldown(Long userId) {
        Instant lastRequest = cooldownMap.get(userId);
        if (lastRequest == null) {
            return false;
        }
        return Duration.between(lastRequest, Instant.now()).getSeconds() < COOLDOWN_SECONDS;
    }

    /**
     * Returns the remaining cooldown seconds for the given user.
     *
     * @param userId the user's ID
     * @return remaining seconds (0 if not on cooldown)
     */
    public long getRemainingCooldownSeconds(Long userId) {
        Instant lastRequest = cooldownMap.get(userId);
        if (lastRequest == null) {
            return 0;
        }
        long elapsed = Duration.between(lastRequest, Instant.now()).getSeconds();
        long remaining = COOLDOWN_SECONDS - elapsed;
        return Math.max(0, remaining);
    }

    /**
     * Builds the user prompt from portfolio summary data.
     */
    private String buildUserPrompt(List<PortfolioSummaryResponse> summary) {
        if (summary.isEmpty()) {
            return "Analyze this portfolio:\n(empty portfolio — no holdings)";
        }

        StringBuilder sb = new StringBuilder("Analyze this portfolio:\n");
        for (PortfolioSummaryResponse item : summary) {
            sb.append("- ").append(item.symbol()).append(": ")
                    .append(item.totalQuantity()).append(" shares");
            if (item.weightedAverageCost() != null) {
                sb.append(", avg cost $").append(item.weightedAverageCost().toPlainString());
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * Builds the Bedrock request body JSON using the Messages API format.
     */
    private String buildRequestBody(String userPrompt) {
        // Escape special characters for JSON embedding
        String escapedUserPrompt = escapeJson(userPrompt);
        String escapedSystemPrompt = escapeJson(SYSTEM_PROMPT);

        return """
                {
                  "anthropic_version": "bedrock-2023-05-31",
                  "max_tokens": 1024,
                  "messages": [
                    {
                      "role": "user",
                      "content": "%s"
                    }
                  ],
                  "system": "%s"
                }
                """.formatted(escapedUserPrompt, escapedSystemPrompt);
    }

    /**
     * Extracts the text content from the Bedrock response JSON.
     * Expected structure: { "content": [{ "type": "text", "text": "..." }] }
     */
    private String extractResponseText(InvokeModelResponse response) {
        try {
            String json = response.body().asUtf8String();
            JsonNode root = objectMapper.readTree(json);
            JsonNode content = root.path("content");
            if (content.isArray() && !content.isEmpty()) {
                return content.get(0).path("text").asText("");
            }
            return root.path("completion").asText("");
        } catch (Exception e) {
            log.error("InsightsService: failed to parse Bedrock response", e);
            return "";
        }
    }

    /**
     * Parses the response text by looking for section headers (ALLOCATION:, RISK:, SUGGESTIONS:).
     * If parsing fails, the full text is returned in the allocation field with empty risk and suggestions.
     */
    InsightsResponse parseResponse(String text) {
        String allocation = "";
        String risk = "";
        String suggestions = "";

        int allocIdx = indexOfIgnoreCase(text, "ALLOCATION:");
        int riskIdx = indexOfIgnoreCase(text, "RISK:");
        int sugIdx = indexOfIgnoreCase(text, "SUGGESTIONS:");

        if (allocIdx >= 0 && riskIdx >= 0 && sugIdx >= 0) {
            allocation = text.substring(allocIdx + "ALLOCATION:".length(), riskIdx).trim();
            risk = text.substring(riskIdx + "RISK:".length(), sugIdx).trim();
            suggestions = text.substring(sugIdx + "SUGGESTIONS:".length()).trim();
        } else {
            // Fallback: put everything in allocation
            allocation = text.trim();
        }

        // Strip markdown formatting (headers, bold, numbered prefixes)
        allocation = stripMarkdown(allocation);
        risk = stripMarkdown(risk);
        suggestions = stripMarkdown(suggestions);

        return new InsightsResponse(allocation, risk, suggestions, Instant.now());
    }

    /**
     * Strips common markdown formatting from text.
     * Removes # headers, ** bold, leading numbers/bullets, and excess whitespace.
     */
    private String stripMarkdown(String text) {
        if (text == null || text.isEmpty()) return text;
        // Remove markdown header lines (e.g., "## Risk Assessment")
        String result = text.replaceAll("(?m)^#{1,6}\\s+.*$", "").trim();
        // Remove bold markers
        result = result.replace("**", "");
        // Remove leading bullet/number markers (e.g., "1. " or "- ")
        result = result.replaceAll("(?m)^\\d+\\.\\s+", "");
        result = result.replaceAll("(?m)^[-*]\\s+", "");
        // Collapse multiple newlines into single space for clean paragraph display
        result = result.replaceAll("\\n{2,}", "\n").trim();
        return result;
    }

    /**
     * Case-insensitive indexOf for section header detection.
     */
    private int indexOfIgnoreCase(String text, String keyword) {
        return text.toUpperCase().indexOf(keyword.toUpperCase());
    }

    /**
     * Escapes special characters for JSON string embedding.
     */
    private String escapeJson(String input) {
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
