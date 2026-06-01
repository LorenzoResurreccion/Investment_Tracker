package com.investmenttracker.investment;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for investment validation.
 *
 * <p><b>Feature: finnhub-websocket-api, Property 3: investment validation rejects all invalid inputs</b></p>
 *
 * <p><b>Validates: Requirements 7.2, 7.3, 7.6</b></p>
 *
 * <p>Generates investment DTOs with:
 * <ul>
 *   <li>{@code quantity} outside {@code [0.000001, 999999999.99]} (zero, negatives, > max)</li>
 *   <li>symbol strings of length > 20</li>
 *   <li>platform strings of length > 100</li>
 * </ul>
 * Asserts that the Bean Validation layer rejects the request (constraint violations are
 * non-empty), which means the controller would return HTTP 400 and no record would be persisted.
 */
class InvestmentValidationPropertyTest {

    private Validator validator;

    @BeforeProperty
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Property(tries = 100)
    @Label("Invalid quantity (outside [0.000001, 999999999.99]) is rejected by validation")
    void invalidQuantityIsRejected(
            @ForAll("validSymbols") String symbol,
            @ForAll("invalidQuantities") BigDecimal invalidQuantity,
            @ForAll("validOptionalPlatform") String platform
    ) {
        InvestmentRequest request = new InvestmentRequest(
                symbol,
                invalidQuantity,
                platform.isEmpty() ? null : platform
        );

        Set<ConstraintViolation<InvestmentRequest>> violations = validator.validate(request);

        assertThat(violations)
                .isNotEmpty()
                .anyMatch(v -> v.getPropertyPath().toString().equals("quantity"));
    }

    @Property(tries = 100)
    @Label("Symbol exceeding 20 characters is rejected by validation")
    void oversizedSymbolIsRejected(
            @ForAll("oversizedSymbols") String oversizedSymbol,
            @ForAll("validQuantities") BigDecimal quantity,
            @ForAll("validOptionalPlatform") String platform
    ) {
        InvestmentRequest request = new InvestmentRequest(
                oversizedSymbol,
                quantity,
                platform.isEmpty() ? null : platform
        );

        Set<ConstraintViolation<InvestmentRequest>> violations = validator.validate(request);

        assertThat(violations)
                .isNotEmpty()
                .anyMatch(v -> v.getPropertyPath().toString().equals("symbol"));
    }

    @Property(tries = 100)
    @Label("Platform exceeding 100 characters is rejected by validation")
    void oversizedPlatformIsRejected(
            @ForAll("validSymbols") String symbol,
            @ForAll("validQuantities") BigDecimal quantity,
            @ForAll("oversizedPlatforms") String oversizedPlatform
    ) {
        InvestmentRequest request = new InvestmentRequest(
                symbol,
                quantity,
                oversizedPlatform
        );

        Set<ConstraintViolation<InvestmentRequest>> violations = validator.validate(request);

        assertThat(violations)
                .isNotEmpty()
                .anyMatch(v -> v.getPropertyPath().toString().equals("platform"));
    }

    // --- Custom Arbitraries ---

    @Provide
    Arbitrary<String> validSymbols() {
        return Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withChars('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', ':')
                .ofMinLength(1)
                .ofMaxLength(20);
    }

    @Provide
    Arbitrary<BigDecimal> validQuantities() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.000001"), new BigDecimal("999999999.99"))
                .ofScale(8);
    }

    @Provide
    Arbitrary<String> validOptionalPlatform() {
        Arbitrary<String> platformName = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withChars(' ', '-', '_')
                .ofMinLength(1)
                .ofMaxLength(100);

        return Arbitraries.oneOf(
                Arbitraries.just(""),
                platformName
        );
    }

    @Provide
    Arbitrary<BigDecimal> invalidQuantities() {
        // Generate quantities outside [0.000001, 999999999.99]:
        // - zero
        // - negatives
        // - values exceeding the maximum
        Arbitrary<BigDecimal> zero = Arbitraries.just(BigDecimal.ZERO);

        Arbitrary<BigDecimal> negatives = Arbitraries.bigDecimals()
                .between(new BigDecimal("-999999999.99"), new BigDecimal("-0.000001"))
                .ofScale(8);

        Arbitrary<BigDecimal> aboveMax = Arbitraries.bigDecimals()
                .between(new BigDecimal("1000000000.00"), new BigDecimal("9999999999.99"))
                .ofScale(2);

        return Arbitraries.oneOf(zero, negatives, aboveMax);
    }

    @Provide
    Arbitrary<String> oversizedSymbols() {
        // Symbols with length > 20 characters
        return Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withChars('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', ':')
                .ofMinLength(21)
                .ofMaxLength(50);
    }

    @Provide
    Arbitrary<String> oversizedPlatforms() {
        // Platform strings with length > 100 characters
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withChars(' ', '-', '_')
                .ofMinLength(101)
                .ofMaxLength(200);
    }
}
