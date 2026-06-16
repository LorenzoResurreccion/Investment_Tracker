package com.investmenttracker.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests verifying the application exits with a non-zero code and logs
 * ERROR when required environment variables are absent or the database is unreachable.
 *
 * For env-var validation tests: launches the application in a subprocess to safely
 * verify System.exit behavior (Java 21 does not support SecurityManager-based exit
 * interception). The subprocess uses the same classpath as the test JVM.
 *
 * For database unreachable: uses SpringApplication directly with an unreachable
 * database URL and verifies the startup exception.
 *
 * Validates: Requirements 1.3, 8.3, 8.4
 */
class StartupFailureIntegrationTest {

    /**
     * Verifies application exits with non-zero code and logs ERROR when
     * FINNHUB_API_KEY is absent.
     *
     * Validates: Requirement 1.3
     */
    @Test
    void applicationExitsNonZero_andLogsError_whenFinnhubApiKeyIsAbsent() throws Exception {
        // Launch the application in a subprocess without FINNHUB_API_KEY
        ProcessResult result = launchApplicationSubprocess(Map.of(
                "DATABASE_URL", "jdbc:postgresql://localhost:5432/test",
                "DATABASE_USERNAME", "test",
                "DATABASE_PASSWORD", "test",
                "AWS_REGION", "us-east-1",
                "COGNITO_USER_POOL_ID", "us-east-1_TestPool"
                // FINNHUB_API_KEY intentionally omitted
        ));

        // The process should have exited (not hung)
        assertThat(result.finished)
                .as("Application should exit within timeout when FINNHUB_API_KEY is absent")
                .isTrue();

        // The exit code should be non-zero
        assertThat(result.exitCode)
                .as("Application should exit with non-zero code when FINNHUB_API_KEY is absent")
                .isNotEqualTo(0);

        // Output should contain ERROR log mentioning FINNHUB_API_KEY
        assertThat(result.output)
                .as("Application should log ERROR explicitly naming FINNHUB_API_KEY")
                .contains("FINNHUB_API_KEY");
        assertThat(result.output)
                .as("Application should log at ERROR level")
                .contains("ERROR");
    }

    /**
     * Verifies application exits with non-zero code and logs ERROR when
     * DATABASE_URL is absent.
     *
     * Validates: Requirement 8.4
     */
    @Test
    void applicationExitsNonZero_andLogsError_whenDatabaseUrlIsAbsent() throws Exception {
        // Launch the application in a subprocess without DATABASE_URL
        ProcessResult result = launchApplicationSubprocess(Map.of(
                "FINNHUB_API_KEY", "test-key",
                "DATABASE_USERNAME", "test",
                "DATABASE_PASSWORD", "test",
                "AWS_REGION", "us-east-1",
                "COGNITO_USER_POOL_ID", "us-east-1_TestPool"
                // DATABASE_URL intentionally omitted
        ));

        // The process should have exited
        assertThat(result.finished)
                .as("Application should exit within timeout when DATABASE_URL is absent")
                .isTrue();

        // The exit code should be non-zero
        assertThat(result.exitCode)
                .as("Application should exit with non-zero code when DATABASE_URL is absent")
                .isNotEqualTo(0);

        // Output should contain ERROR log mentioning DATABASE_URL
        assertThat(result.output)
                .as("Application should log ERROR identifying missing DATABASE_URL")
                .contains("DATABASE_URL");
        assertThat(result.output)
                .as("Application should log at ERROR level")
                .contains("ERROR");
    }

    /**
     * Verifies application exits with non-zero code and logs ERROR when
     * DATABASE_URL contains a malformed JDBC URL (missing jdbc: prefix).
     *
     * Validates: Requirement 8.4
     */
    @Test
    void applicationExitsNonZero_andLogsError_whenDatabaseUrlIsMalformed() throws Exception {
        // Launch the application with a malformed DATABASE_URL (missing jdbc: prefix)
        ProcessResult result = launchApplicationSubprocess(Map.of(
                "FINNHUB_API_KEY", "test-key",
                "DATABASE_URL", "postgresql://localhost:5432/test", // malformed: no jdbc: prefix
                "DATABASE_USERNAME", "test",
                "DATABASE_PASSWORD", "test",
                "AWS_REGION", "us-east-1",
                "COGNITO_USER_POOL_ID", "us-east-1_TestPool"
        ));

        // The process should have exited
        assertThat(result.finished)
                .as("Application should exit within timeout when DATABASE_URL is malformed")
                .isTrue();

        // The exit code should be non-zero
        assertThat(result.exitCode)
                .as("Application should exit with non-zero code when DATABASE_URL is malformed")
                .isNotEqualTo(0);

        // Output should contain ERROR log mentioning DATABASE_URL and malformed
        assertThat(result.output)
                .as("Application should log ERROR identifying malformed DATABASE_URL")
                .contains("DATABASE_URL");
        assertThat(result.output)
                .as("Application should indicate the URL is malformed")
                .containsIgnoringCase("malformed");
    }

    /**
     * Verifies application fails to start when the database is unreachable.
     * Uses SpringApplication directly with an unreachable database URL and
     * a short connection timeout.
     *
     * Validates: Requirement 8.3
     */
    @Test
    void applicationFailsToStart_whenDatabaseIsUnreachable() {
        // Use SpringApplication programmatically with an unreachable DB
        SpringApplication app = new SpringApplication(
                com.investmenttracker.InvestmentTrackerApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);

        java.util.Map<String, Object> props = new java.util.HashMap<>();
        // Valid JDBC URL but pointing to a non-routable address (RFC 5737 TEST-NET)
        props.put("spring.datasource.url", "jdbc:postgresql://192.0.2.1:5432/nonexistent");
        props.put("spring.datasource.username", "test");
        props.put("spring.datasource.password", "test");
        props.put("FINNHUB_API_KEY", "test-key");
        props.put("DATABASE_URL", "jdbc:postgresql://192.0.2.1:5432/nonexistent");
        props.put("AWS_REGION", "us-east-1");
        props.put("COGNITO_USER_POOL_ID", "us-east-1_TestPool");
        // Short connection timeout so the test doesn't wait 30s
        props.put("spring.datasource.hikari.connection-timeout", "3000");
        props.put("spring.datasource.hikari.initialization-fail-timeout", "1");
        props.put("spring.flyway.enabled", "false");
        app.setDefaultProperties(props);

        // Act & Assert: application should fail to start due to unreachable database
        assertThatThrownBy(() -> app.run())
                .as("Application should fail to start with unreachable database")
                .isNotNull();
    }

    // --- Helper methods ---

    /**
     * Launches the Spring Boot application in a subprocess with the given
     * environment variables. Returns the process result including exit code
     * and captured output.
     *
     * Handles Surefire's manifest-only JAR classpath by reading the actual
     * classpath from the manifest when needed.
     */
    private ProcessResult launchApplicationSubprocess(Map<String, String> envVars) throws Exception {
        // Build the classpath — handle Surefire's manifest-only JAR
        String classpath = getEffectiveClasspath();
        String javaHome = System.getProperty("java.home");
        String javaBin = Path.of(javaHome, "bin", "java").toString();

        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add("-cp");
        command.add(classpath);
        // Use random port to avoid conflicts between parallel subprocess launches
        command.add("-Dserver.port=0");
        command.add("com.investmenttracker.InvestmentTrackerApplication");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        // Run from temp directory so spring-dotenv doesn't load .env from the project
        pb.directory(new java.io.File(System.getProperty("java.io.tmpdir")));

        // Clear environment variables that might interfere
        Map<String, String> env = pb.environment();
        env.remove("FINNHUB_API_KEY");
        env.remove("DATABASE_URL");
        env.remove("DATABASE_USERNAME");
        env.remove("DATABASE_PASSWORD");

        // Set the provided environment variables
        env.putAll(envVars);

        Process process = pb.start();

        // Read output in a separate thread to prevent blocking
        StringBuilder outputBuilder = new StringBuilder();
        Thread outputReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputBuilder.append(line).append("\n");
                }
            } catch (Exception e) {
                // Ignore read errors on process termination
            }
        });
        outputReader.start();

        boolean finished = process.waitFor(60, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
        }

        outputReader.join(5000);

        return new ProcessResult(finished, finished ? process.exitValue() : -1, outputBuilder.toString());
    }

    /**
     * Gets the effective classpath, handling Surefire's manifest-only JAR.
     * When Surefire uses a manifest JAR (default behavior), the java.class.path
     * property contains only the manifest JAR. We need to read the actual
     * classpath from the manifest's Class-Path attribute.
     */
    private String getEffectiveClasspath() throws Exception {
        String rawClasspath = System.getProperty("java.class.path");

        // If the classpath already contains multiple entries or target/classes, use it directly
        if (rawClasspath.contains(java.io.File.pathSeparator) ||
                rawClasspath.contains("target" + java.io.File.separator + "classes")) {
            return rawClasspath;
        }

        // Check if this is a Surefire manifest-only JAR
        String[] entries = rawClasspath.split(java.io.File.pathSeparator);
        for (String entry : entries) {
            java.io.File file = new java.io.File(entry);
            if (file.isFile() && file.getName().endsWith(".jar")) {
                try (java.util.jar.JarFile jar = new java.util.jar.JarFile(file)) {
                    java.util.jar.Manifest manifest = jar.getManifest();
                    if (manifest != null) {
                        String manifestClassPath = manifest.getMainAttributes()
                                .getValue("Class-Path");
                        if (manifestClassPath != null && !manifestClassPath.isBlank()) {
                            // Convert space-separated URLs to file paths
                            String[] urls = manifestClassPath.split("\\s+");
                            StringBuilder sb = new StringBuilder();
                            for (String url : urls) {
                                if (sb.length() > 0) {
                                    sb.append(java.io.File.pathSeparator);
                                }
                                if (url.startsWith("file:")) {
                                    // Decode URL-encoded characters (e.g., %20 for spaces)
                                    java.net.URI uri = java.net.URI.create(url);
                                    sb.append(Path.of(uri).toString());
                                } else {
                                    sb.append(url);
                                }
                            }
                            return sb.toString();
                        }
                    }
                }
            }
        }

        return rawClasspath;
    }

    private record ProcessResult(boolean finished, int exitCode, String output) {}
}
