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
 * Property-based test for negative averageCost rejection.
 *
 * <p><b>Feature: average-cost-profit-loss, Property 2: Negative averageCost rejection</b></p>
 *
 * <p><b>Validates: Requirements 1.6</b></p>
 *
 * <p>For any negative BigDecimal value, attempting to create an investment with that value
 * as averageCost SHALL result in a validation error (constraint violation on averageCost),
 * which means the controller would return HTTP 400.</p>
 */
class AverageCostValidationPropertyTest {

    private Validator validator;

    @BeforeProperty
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Property(tries = 100)
    @Label("Negative averageCost is rejected by validation (HTTP 400)")
    void negativeAverageCostIsRejected(
            @ForAll("negativeAverageCosts") BigDecimal negativeAverageCost
    ) {
        // Build a valid request except for the negative averageCost
        InvestmentRequest request = new InvestmentRequest("AAPL", new BigDecimal("10.0"), "TestPlatform");
        request.setAverageCost(negativeAverageCost);

        Set<ConstraintViolation<InvestmentRequest>> violations = validator.validate(request);

        // Assert that validation rejects the request due to averageCost
        assertThat(violations)
                .isNotEmpty()
                .anyMatch(v -> v.getPropertyPath().toString().equals("averageCost"));

        // Assert the message matches the expected validation error
        assertThat(violations)
                .anyMatch(v -> v.getMessage().equals("averageCost must be zero or greater"));
    }

    // --- Custom Arbitraries ---

    @Provide
    Arbitrary<BigDecimal> negativeAverageCosts() {
        // Generate random negative BigDecimals with up to 8 decimal places
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("-999999999999999999.99999999"), new BigDecimal("-0.00000001"))
                .ofScale(8);
    }
}
