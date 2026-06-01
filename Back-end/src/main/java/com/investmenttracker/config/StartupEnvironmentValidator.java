package com.investmenttracker.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Validates required environment variables during application startup.
 *
 * Implemented as a {@link BeanFactoryPostProcessor} with {@link PriorityOrdered}
 * to ensure it runs before any regular beans (including DataSource/Flyway) are
 * created. This guarantees that missing or malformed environment variables are
 * detected and reported with clear ERROR messages before the application
 * attempts to connect to external services.
 *
 * Checks performed:
 * - FINNHUB_API_KEY — must be present and non-blank.
 * - DATABASE_URL — must be present, non-blank, and start with "jdbc:"
 *   (malformed-URL guard).
 *
 * If either check fails, logs an ERROR and calls System.exit(1).
 *
 * Requirements: 1.3, 8.4
 */
@Component
public class StartupEnvironmentValidator implements BeanFactoryPostProcessor, EnvironmentAware, PriorityOrdered {

    private static final Logger log = LoggerFactory.getLogger(StartupEnvironmentValidator.class);

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        boolean valid = true;

        // --- FINNHUB_API_KEY ---
        String apiKey = environment.getProperty("FINNHUB_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            log.error(
                "[StartupEnvironmentValidator] Startup validation failed: environment variable " +
                "FINNHUB_API_KEY is absent or blank. " +
                "Set FINNHUB_API_KEY before starting the application."
            );
            valid = false;
        }

        // --- DATABASE_URL ---
        String dbUrl = environment.getProperty("DATABASE_URL");
        if (dbUrl == null || dbUrl.isBlank()) {
            log.error(
                "[StartupEnvironmentValidator] Startup validation failed: environment variable " +
                "DATABASE_URL is absent or blank. " +
                "Set DATABASE_URL to a valid JDBC URL (e.g. jdbc:postgresql://host:5432/db)."
            );
            valid = false;
        } else if (!dbUrl.startsWith("jdbc:")) {
            log.error(
                "[StartupEnvironmentValidator] Startup validation failed: DATABASE_URL is malformed " +
                "(does not start with 'jdbc:'). Provided value: '{}'. " +
                "A valid JDBC URL must begin with 'jdbc:' (e.g. jdbc:postgresql://host:5432/db).",
                dbUrl
            );
            valid = false;
        }

        if (!valid) {
            Runtime.getRuntime().halt(1);
        }
    }
}
