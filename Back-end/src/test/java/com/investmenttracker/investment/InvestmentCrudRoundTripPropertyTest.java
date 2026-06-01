package com.investmenttracker.investment;

import com.investmenttracker.finnhub.FinnhubClient;
import com.investmenttracker.finnhub.SubscriptionManager;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based test for Investment CRUD round-trip.
 *
 * <p><b>Feature: finnhub-websocket-api, Property 8: investment CRUD round-trip preserves data</b></p>
 *
 * <p><b>Validates: Requirements 7.1, 7.2, 7.4, 7.7</b></p>
 *
 * <p>Generates random valid investment DTOs (symbol ≤ 20 chars, quantity in range
 * [0.000001, 999999999.99], optional platform ≤ 100 chars). Asserts that after POST
 * (createInvestment), the returned record has all fields matching the input plus a
 * generated id and createdAt; after DELETE (deleteInvestment), the record is absent
 * from GET (getAllInvestments).</p>
 */
class InvestmentCrudRoundTripPropertyTest {

    private InvestmentRepository investmentRepository;
    private SubscriptionManager subscriptionManager;
    private FinnhubClient finnhubClient;
    private InvestmentService investmentService;

    // In-memory store to simulate the repository
    private Map<Long, Investment> store;
    private AtomicLong idGenerator;

    @BeforeProperty
    void setUp() {
        store = new HashMap<>();
        idGenerator = new AtomicLong(1);

        investmentRepository = mock(InvestmentRepository.class);
        subscriptionManager = mock(SubscriptionManager.class);
        finnhubClient = mock(FinnhubClient.class);

        // Mock save: assign id and createdAt, store in map
        when(investmentRepository.save(any(Investment.class))).thenAnswer(invocation -> {
            Investment entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(idGenerator.getAndIncrement());
                entity.setCreatedAt(OffsetDateTime.now());
            }
            store.put(entity.getId(), entity);
            return entity;
        });

        // Mock findAll: return all stored investments
        when(investmentRepository.findAll()).thenAnswer(invocation ->
                new ArrayList<>(store.values()));

        // Mock findById: look up in store
        when(investmentRepository.findById(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return Optional.ofNullable(store.get(id));
        });

        // Mock delete: remove from store
        doAnswer(invocation -> {
            Investment entity = invocation.getArgument(0);
            store.remove(entity.getId());
            return null;
        }).when(investmentRepository).delete(any(Investment.class));

        // Mock existsBySymbol: check if any investment in store has the symbol
        when(investmentRepository.existsBySymbol(anyString())).thenAnswer(invocation -> {
            String symbol = invocation.getArgument(0);
            return store.values().stream().anyMatch(inv -> inv.getSymbol().equals(symbol));
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
    @Label("CRUD round-trip: createInvestment preserves all fields and deleteInvestment removes the record")
    void crudRoundTripPreservesData(
            @ForAll("validSymbols") String symbol,
            @ForAll("validQuantities") BigDecimal quantity,
            @ForAll("optionalPlatform") String platform
    ) {
        // --- CREATE (simulates POST /api/investments) ---
        InvestmentRequest request = new InvestmentRequest(symbol, quantity, platform.isEmpty() ? null : platform);
        Investment created = investmentService.createInvestment(request);

        // Verify generated fields exist
        assertThat(created.getId()).isNotNull();
        assertThat(created.getCreatedAt()).isNotNull();

        // Verify input fields are preserved (Requirement 7.2, 7.7)
        assertThat(created.getSymbol()).isEqualTo(symbol);
        assertThat(created.getQuantity()).isEqualByComparingTo(quantity);
        if (platform.isEmpty()) {
            assertThat(created.getPlatform()).isNull();
        } else {
            assertThat(created.getPlatform()).isEqualTo(platform);
        }

        // --- READ (simulates GET /api/investments) — Requirement 7.1 ---
        List<Investment> allInvestments = investmentService.getAllInvestments();
        assertThat(allInvestments).isNotEmpty();

        Investment fetched = allInvestments.stream()
                .filter(inv -> inv.getId().equals(created.getId()))
                .findFirst()
                .orElse(null);
        assertThat(fetched).isNotNull();
        assertThat(fetched.getSymbol()).isEqualTo(symbol);
        assertThat(fetched.getQuantity()).isEqualByComparingTo(quantity);
        assertThat(fetched.getCreatedAt()).isEqualTo(created.getCreatedAt());

        // --- DELETE (simulates DELETE /api/investments/{id}) — Requirement 7.4 ---
        investmentService.deleteInvestment(created.getId());

        // Verify record is absent from GET
        List<Investment> allAfterDelete = investmentService.getAllInvestments();
        boolean stillPresent = allAfterDelete.stream()
                .anyMatch(inv -> inv.getId().equals(created.getId()));
        assertThat(stillPresent).isFalse();
    }

    // --- Custom Arbitraries ---

    @Provide
    Arbitrary<String> validSymbols() {
        // Symbol: 1-20 alphanumeric characters (including : for crypto symbols like BINANCE:BTCUSDT)
        return Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withChars('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', ':')
                .ofMinLength(1)
                .ofMaxLength(20);
    }

    @Provide
    Arbitrary<BigDecimal> validQuantities() {
        // Quantity: positive decimal in range [0.000001, 999999999.99] with up to 8 decimal places
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.000001"), new BigDecimal("999999999.99"))
                .ofScale(8);
    }

    @Provide
    Arbitrary<String> optionalPlatform() {
        // Generate either empty string (no platform) or a valid platform name 1-100 chars
        Arbitrary<String> platformName = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars(' ', '-', '_')
                .ofMinLength(1)
                .ofMaxLength(100);

        return Arbitraries.oneOf(
                Arbitraries.just(""),
                platformName
        );
    }
}
