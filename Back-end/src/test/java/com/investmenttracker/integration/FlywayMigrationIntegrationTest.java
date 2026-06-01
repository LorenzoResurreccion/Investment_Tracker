package com.investmenttracker.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying Flyway migrations apply cleanly on a fresh PostgreSQL database.
 * Uses @DataJpaTest to load only the JPA/DataSource/Flyway slice without Kafka or WebSocket beans.
 *
 * Validates: Requirements 8.1, 8.5
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FlywayMigrationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("investment_tracker_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private DataSource dataSource;

    @Test
    void flywayMigrationsApplyCleanlyOnFreshDatabase() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // Verify the investments table exists
            try (ResultSet tables = metaData.getTables(null, "public", "investments", new String[]{"TABLE"})) {
                assertThat(tables.next())
                        .as("investments table should exist after Flyway migration")
                        .isTrue();
            }
        }
    }

    @Test
    void investmentsTableHasExpectedColumns() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            Map<String, String> columns = new HashMap<>();
            try (ResultSet rs = metaData.getColumns(null, "public", "investments", null)) {
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    String typeName = rs.getString("TYPE_NAME");
                    columns.put(columnName, typeName);
                }
            }

            // Verify all expected columns exist
            assertThat(columns).containsKey("id");
            assertThat(columns).containsKey("symbol");
            assertThat(columns).containsKey("quantity");
            assertThat(columns).containsKey("platform");
            assertThat(columns).containsKey("created_at");

            // Verify column types
            assertThat(columns.get("id")).isEqualTo("bigserial");
            assertThat(columns.get("symbol")).containsIgnoringCase("varchar");
            assertThat(columns.get("quantity")).containsIgnoringCase("numeric");
            assertThat(columns.get("platform")).containsIgnoringCase("varchar");
            assertThat(columns.get("created_at")).containsIgnoringCase("timestamptz");
        }
    }

    @Test
    void flywaySchemaHistoryTableExists() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // Verify Flyway's schema history table was created (confirms Flyway ran)
            try (ResultSet tables = metaData.getTables(null, "public", "flyway_schema_history", new String[]{"TABLE"})) {
                assertThat(tables.next())
                        .as("flyway_schema_history table should exist, confirming Flyway executed")
                        .isTrue();
            }
        }
    }

    @Test
    void investmentsTableHasCorrectConstraints() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // Verify primary key on id column
            try (ResultSet pk = metaData.getPrimaryKeys(null, "public", "investments")) {
                assertThat(pk.next())
                        .as("investments table should have a primary key")
                        .isTrue();
                assertThat(pk.getString("COLUMN_NAME")).isEqualTo("id");
            }

            // Verify NOT NULL constraints via column nullable metadata
            try (ResultSet rs = metaData.getColumns(null, "public", "investments", "symbol")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt("NULLABLE"))
                        .as("symbol column should be NOT NULL")
                        .isEqualTo(DatabaseMetaData.columnNoNulls);
            }

            try (ResultSet rs = metaData.getColumns(null, "public", "investments", "quantity")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt("NULLABLE"))
                        .as("quantity column should be NOT NULL")
                        .isEqualTo(DatabaseMetaData.columnNoNulls);
            }

            try (ResultSet rs = metaData.getColumns(null, "public", "investments", "created_at")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt("NULLABLE"))
                        .as("created_at column should be NOT NULL")
                        .isEqualTo(DatabaseMetaData.columnNoNulls);
            }

            // Verify platform is nullable
            try (ResultSet rs = metaData.getColumns(null, "public", "investments", "platform")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt("NULLABLE"))
                        .as("platform column should be nullable")
                        .isEqualTo(DatabaseMetaData.columnNullable);
            }
        }
    }
}
