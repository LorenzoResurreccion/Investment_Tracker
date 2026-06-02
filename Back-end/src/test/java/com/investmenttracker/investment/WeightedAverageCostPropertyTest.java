package com.investmenttracker.investment;

import com.investmenttracker.finnhub.FinnhubClient;
import com.investmenttracker.finnhub.SubscriptionManager;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based test for weighted average cost computation.
 *
 * <p><b>Feature: average-cost-profit-loss, Property 3: Weighted average cost computation</b></p>
 *
 * <p><b>Validates: Requirements 2.2, 2.3, 2.4, 2.5</b></p>
 *
 * <p>For any list of holdings for a symbol where each holding has a quantity (&gt; 0) and an
 * optional averageCost (nullable, non-negative), the computed weighted average cost SHALL equal
 * SUM(averageCost_i × quantity_i) / SUM(quantity_i) considering only holdings where averageCost
 * is non-null, rounded to 8 decimal places using half-up rounding. If no holdings have a non-null
 * averageCost, or if the sum of their quantities is zero, the result SHALL be null.</p>
 */
class WeightedAverageCostPropertyTest {

    private InvestmentRepository investmentRepository;
    private SubscriptionManager subscriptionManager;
    private FinnhubClient finnhubClient;
    private InvestmentService investmentService;

    private AtomicLong idGenerator;

    @BeforeProperty
    void setUp() {
        idGenerator = new AtomicLong(1);

        investmentRepository = mock(InvestmentRepository.class);
        subscriptionManager = mock(SubscriptionManager.class);
        finnhubClient = mock(FinnhubClient.class);

        investmentService = new InvestmentService(
                investmentRepository,
                subscriptionManager,
                finnhubClient
        );
    }

    @Property(tries = 100)
    @Label("Weighted average cost matches SUM(avgCost_i × qty_i) / SUM(qty_i) for non-null entries, rounded to 8dp HALF_UP")
    void weightedAverageCostMatchesFormula(
            @ForAll("investmentLists") List<Investment> investments
    ) {
        // Mock findAll to return the generated investments
        when(investmentRepository.findAll()).thenReturn(investments);

        // Call getPortfolioSummary
        List<PortfolioSummaryResponse> summary = investmentService.getPortfolioSummary();

        // There should be exactly 1 summary entry (all investments share the same symbol)
        assertThat(summary).hasSize(1);

        PortfolioSummaryResponse response = summary.get(0);

        // Independently compute the expected weighted average cost
        BigDecimal expectedWeightedAvgCost = computeExpectedWeightedAverage(investments);

        if (expectedWeightedAvgCost == null) {
            assertThat(response.weightedAverageCost()).isNull();
        } else {
            assertThat(response.weightedAverageCost()).isEqualByComparingTo(expectedWeightedAvgCost);
        }
    }

    @Property(tries = 100)
    @Label("When ALL averageCosts are null, weighted average cost is null")
    void allNullAverageCostsResultInNull(
            @ForAll("allNullAverageCostLists") List<Investment> investments
    ) {
        when(investmentRepository.findAll()).thenReturn(investments);

        List<PortfolioSummaryResponse> summary = investmentService.getPortfolioSummary();

        assertThat(summary).hasSize(1);
        assertThat(summary.get(0).weightedAverageCost()).isNull();
    }

    @Property(tries = 100)
    @Label("Weighted average cost is between min and max non-null averageCost values (bounds property)")
    void weightedAverageIsBoundedByMinAndMax(
            @ForAll("investmentListsWithAtLeastOneNonNull") List<Investment> investments
    ) {
        when(investmentRepository.findAll()).thenReturn(investments);

        List<PortfolioSummaryResponse> summary = investmentService.getPortfolioSummary();

        assertThat(summary).hasSize(1);

        PortfolioSummaryResponse response = summary.get(0);
        assertThat(response.weightedAverageCost()).isNotNull();

        // Find min and max non-null averageCosts
        BigDecimal minCost = investments.stream()
                .map(Investment::getAverageCost)
                .filter(c -> c != null)
                .min(BigDecimal::compareTo)
                .orElseThrow();

        BigDecimal maxCost = investments.stream()
                .map(Investment::getAverageCost)
                .filter(c -> c != null)
                .max(BigDecimal::compareTo)
                .orElseThrow();

        assertThat(response.weightedAverageCost()).isGreaterThanOrEqualTo(
                minCost.setScale(8, RoundingMode.HALF_UP));
        assertThat(response.weightedAverageCost()).isLessThanOrEqualTo(
                maxCost.setScale(8, RoundingMode.HALF_UP));
    }

    // --- Helper method ---

    private BigDecimal computeExpectedWeightedAverage(List<Investment> holdings) {
        BigDecimal numerator = BigDecimal.ZERO;
        BigDecimal denominator = BigDecimal.ZERO;
        for (Investment h : holdings) {
            if (h.getAverageCost() != null) {
                numerator = numerator.add(h.getAverageCost().multiply(h.getQuantity()));
                denominator = denominator.add(h.getQuantity());
            }
        }
        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return numerator.divide(denominator, 8, RoundingMode.HALF_UP);
    }

    // --- Custom Arbitraries ---

    @Provide
    Arbitrary<List<Investment>> investmentLists() {
        return investmentArbitrary().list().ofMinSize(1).ofMaxSize(20);
    }

    @Provide
    Arbitrary<List<Investment>> allNullAverageCostLists() {
        return nullAverageCostInvestmentArbitrary().list().ofMinSize(1).ofMaxSize(20);
    }

    @Provide
    Arbitrary<List<Investment>> investmentListsWithAtLeastOneNonNull() {
        // At least one investment with non-null averageCost
        Arbitrary<Investment> nonNullCostInvestment = nonNullAverageCostInvestmentArbitrary();
        Arbitrary<List<Investment>> restList = investmentArbitrary().list().ofMinSize(0).ofMaxSize(19);

        return Combinators.combine(nonNullCostInvestment, restList).as((required, rest) -> {
            List<Investment> combined = new ArrayList<>();
            combined.add(required);
            combined.addAll(rest);
            return combined;
        });
    }

    private Arbitrary<Investment> investmentArbitrary() {
        Arbitrary<BigDecimal> quantities = Arbitraries.bigDecimals()
                .between(new BigDecimal("0.000001"), new BigDecimal("999999999.99"))
                .ofScale(8);

        Arbitrary<BigDecimal> nullableAverageCosts = Arbitraries.oneOf(
                Arbitraries.just((BigDecimal) null),
                Arbitraries.bigDecimals()
                        .between(BigDecimal.ZERO, new BigDecimal("999999999.99999999"))
                        .ofScale(8)
        );

        return Combinators.combine(quantities, nullableAverageCosts).as((qty, avgCost) -> {
            Investment inv = new Investment();
            inv.setId(idGenerator.getAndIncrement());
            inv.setSymbol("TEST");
            inv.setQuantity(qty);
            inv.setAverageCost(avgCost);
            inv.setCreatedAt(OffsetDateTime.now());
            return inv;
        });
    }

    private Arbitrary<Investment> nullAverageCostInvestmentArbitrary() {
        Arbitrary<BigDecimal> quantities = Arbitraries.bigDecimals()
                .between(new BigDecimal("0.000001"), new BigDecimal("999999999.99"))
                .ofScale(8);

        return quantities.map(qty -> {
            Investment inv = new Investment();
            inv.setId(idGenerator.getAndIncrement());
            inv.setSymbol("TEST");
            inv.setQuantity(qty);
            inv.setAverageCost(null);
            inv.setCreatedAt(OffsetDateTime.now());
            return inv;
        });
    }

    private Arbitrary<Investment> nonNullAverageCostInvestmentArbitrary() {
        Arbitrary<BigDecimal> quantities = Arbitraries.bigDecimals()
                .between(new BigDecimal("0.000001"), new BigDecimal("999999999.99"))
                .ofScale(8);

        Arbitrary<BigDecimal> averageCosts = Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, new BigDecimal("999999999.99999999"))
                .ofScale(8);

        return Combinators.combine(quantities, averageCosts).as((qty, avgCost) -> {
            Investment inv = new Investment();
            inv.setId(idGenerator.getAndIncrement());
            inv.setSymbol("TEST");
            inv.setQuantity(qty);
            inv.setAverageCost(avgCost);
            inv.setCreatedAt(OffsetDateTime.now());
            return inv;
        });
    }
}
