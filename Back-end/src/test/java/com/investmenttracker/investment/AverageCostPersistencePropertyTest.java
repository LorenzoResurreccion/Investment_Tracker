package com.investmenttracker.investment;

import com.investmenttracker.finnhub.FinnhubClient;
import com.investmenttracker.finnhub.SubscriptionManager;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based test for averageCost round-trip persistence.
 *
 * <p><b>Feature: average-cost-profit-loss, Property 1: Average cost round-trip persistence</b></p>
 *
 * <p><b>Validates: Requirements 1.3, 1.4, 2.1</b></p>
 *
 * <p>For any valid averageCost value (a non-negative BigDecimal with at most 18 integer digits
 * and 8 fractional digits), creating an investment with that averageCost and then reading it
 * back via the service SHALL return the same averageCost value. Updating the averageCost
 * and reading back SHALL also preserve the new value exactly.</p>
 */
class AverageCostPersistencePropertyTest {

    private InvestmentRepository investmentRepository;
    private SubscriptionManager subscriptionManager;
    private FinnhubClient finnhubClient;
    private InvestmentService investmentService;

    private Map<Long, Investment> store;
    private AtomicLong idGenerator;

    @BeforeProperty
    void setUp() {
        store = new HashMap<>();
        idGenerator = new AtomicLong(1);

        investmentRepository = mock(InvestmentRepository.class);
        subscriptionManager = mock(SubscriptionManager.class);
        finnhubClient = mock(FinnhubClient.class);

        // Mock save: assign id and createdAt, store in map (simulates DB persistence)
        when(investmentRepository.save(any(Investment.class))).thenAnswer(invocation -> {
            Investment entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(idGenerator.getAndIncrement());
                entity.setCreatedAt(OffsetDateTime.now());
            }
            // Simulate DB round-trip by creating a copy (as JPA would return a managed entity)
            Investment stored = new Investment();
            stored.setId(entity.getId());
            stored.setSymbol(entity.getSymbol());
            stored.setQuantity(entity.getQuantity());
            stored.setPlatform(entity.getPlatform());
            stored.setAverageCost(entity.getAverageCost());
            stored.setCreatedAt(entity.getCreatedAt());
            store.put(stored.getId(), stored);
            return entity;
        });

        // Mock findById: look up in store (simulates DB read)
        when(investmentRepository.findById(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            Investment found = store.get(id);
            if (found == null) return Optional.empty();
            // Return a copy to simulate detached entity read
            Investment copy = new Investment();
            copy.setId(found.getId());
            copy.setSymbol(found.getSymbol());
            copy.setQuantity(found.getQuantity());
            copy.setPlatform(found.getPlatform());
            copy.setAverageCost(found.getAverageCost());
            copy.setCreatedAt(found.getCreatedAt());
            return Optional.of(copy);
        });

        // Mock findAll: return all stored investments
        when(investmentRepository.findAll()).thenAnswer(invocation ->
                new ArrayList<>(store.values()));

        // Mock existsBySymbol
        when(investmentRepository.existsBySymbol(anyString())).thenAnswer(invocation -> {
            String symbol = invocation.getArgument(0);
            return store.values().stream().anyMatch(inv -> inv.getSymbol().equals(symbol));
        });

        // Mock existsBySymbolAndIdNot
        when(investmentRepository.existsBySymbolAndIdNot(anyString(), anyLong())).thenAnswer(invocation -> {
            String symbol = invocation.getArgument(0);
            Long id = invocation.getArgument(1);
            return store.values().stream()
                    .anyMatch(inv -> inv.getSymbol().equals(symbol) && !inv.getId().equals(id));
        });

        // SubscriptionManager.add always returns true (symbol is new)
        when(subscriptionManager.add(any())).thenReturn(true);

        investmentService = new InvestmentService(
                investmentRepository,
                subscriptionManager,
                finnhubClient
        );
    }

    @Property(tries = 100)
    @Label("averageCost round-trip: create with valid averageCost, read back, value is exactly preserved")
    void averageCostRoundTripPersistence(
            @ForAll("validAverageCosts") BigDecimal averageCost
    ) {
        // Create an investment with the generated averageCost (Requirement 1.3)
        InvestmentRequest request = new InvestmentRequest("TEST", new BigDecimal("1.0"), "TestPlatform");
        request.setAverageCost(averageCost);

        Investment created = investmentService.createInvestment(request);
        assertThat(created.getId()).isNotNull();

        // Verify the created entity has the correct averageCost
        assertThat(created.getAverageCost()).isEqualByComparingTo(averageCost);

        // Read it back from the store (simulates DB read)
        Investment fetched = investmentRepository.findById(created.getId()).orElse(null);
        assertThat(fetched).isNotNull();

        // Assert averageCost is exactly preserved through the round-trip
        assertThat(fetched.getAverageCost()).isNotNull();
        assertThat(fetched.getAverageCost()).isEqualByComparingTo(averageCost);

        // Verify via the response DTO mapping (Requirement 2.1)
        InvestmentResponse response = InvestmentResponse.from(fetched);
        assertThat(response.averageCost()).isEqualByComparingTo(averageCost);
    }

    @Property(tries = 100)
    @Label("averageCost round-trip via update: update with valid averageCost, read back, value is exactly preserved")
    void averageCostUpdateRoundTripPersistence(
            @ForAll("validAverageCosts") BigDecimal initialCost,
            @ForAll("validAverageCosts") BigDecimal updatedCost
    ) {
        // Create an investment with the initial averageCost
        InvestmentRequest createRequest = new InvestmentRequest("UPD", new BigDecimal("2.0"), "Platform");
        createRequest.setAverageCost(initialCost);

        Investment created = investmentService.createInvestment(createRequest);
        assertThat(created.getId()).isNotNull();
        assertThat(created.getAverageCost()).isEqualByComparingTo(initialCost);

        // Update with a new averageCost (Requirement 1.4)
        InvestmentRequest updateRequest = new InvestmentRequest(null, null, null);
        updateRequest.setAverageCost(updatedCost);

        Investment updated = investmentService.updateInvestment(created.getId(), updateRequest);

        // Verify the updated entity has the new averageCost
        assertThat(updated.getAverageCost()).isEqualByComparingTo(updatedCost);

        // Read it back and assert the updated value is preserved
        Investment fetched = investmentRepository.findById(updated.getId()).orElse(null);
        assertThat(fetched).isNotNull();
        assertThat(fetched.getAverageCost()).isEqualByComparingTo(updatedCost);

        // Verify via response DTO (Requirement 2.1)
        InvestmentResponse response = InvestmentResponse.from(fetched);
        assertThat(response.averageCost()).isEqualByComparingTo(updatedCost);
    }

    // --- Custom Arbitraries ---

    @Provide
    Arbitrary<BigDecimal> validAverageCosts() {
        // Valid averageCost: non-negative BigDecimal with 0-8 fractional digits
        // Column: precision = 18, scale = 8 → max 10 integer digits + 8 fractional
        // Validation: @Digits(integer = 18, fraction = 8) allows up to 18 integer digits
        // We use the range 0 to 10^18 with 0-8 dp as specified in the task
        return Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, new BigDecimal("999999999999999999"))
                .ofScale(8)
                .filter(bd -> bd.compareTo(BigDecimal.ZERO) >= 0);
    }
}
