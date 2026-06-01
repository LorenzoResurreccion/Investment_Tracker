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
 * Property-based test for partial update behaviour.
 *
 * <p><b>Feature: finnhub-websocket-api, Property 9: partial update modifies only the provided fields</b></p>
 *
 * <p><b>Validates: Requirements 7.3</b></p>
 *
 * <p>Generates an existing investment record with random valid values and a random
 * non-empty subset of {@code {symbol, quantity, platform}} to update with new valid values.
 * Asserts that only the provided fields changed and all other fields retain their
 * original values (including {@code id} and {@code createdAt}).</p>
 */
class InvestmentPartialUpdatePropertyTest {

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

        // Mock save: store the entity
        when(investmentRepository.save(any(Investment.class))).thenAnswer(invocation -> {
            Investment entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(idGenerator.getAndIncrement());
                entity.setCreatedAt(OffsetDateTime.now());
            }
            store.put(entity.getId(), entity);
            return entity;
        });

        // Mock findById: look up in store
        when(investmentRepository.findById(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return Optional.ofNullable(store.get(id));
        });

        // Mock existsBySymbolAndIdNot: check if any other investment has the symbol
        when(investmentRepository.existsBySymbolAndIdNot(anyString(), anyLong())).thenAnswer(invocation -> {
            String symbol = invocation.getArgument(0);
            Long excludeId = invocation.getArgument(1);
            return store.values().stream()
                    .anyMatch(inv -> inv.getSymbol().equals(symbol) && !inv.getId().equals(excludeId));
        });

        // Mock existsBySymbol: check if any investment has the symbol
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
    @Label("Partial update modifies only the provided fields; all other fields retain original values")
    void partialUpdateModifiesOnlyProvidedFields(
            @ForAll("validSymbols") String originalSymbol,
            @ForAll("validQuantities") BigDecimal originalQuantity,
            @ForAll("validPlatforms") String originalPlatform,
            @ForAll("validSymbols") String newSymbol,
            @ForAll("validQuantities") BigDecimal newQuantity,
            @ForAll("validPlatforms") String newPlatform,
            @ForAll("fieldSubsets") Set<String> fieldsToUpdate
    ) {
        // --- Set up an existing investment in the store ---
        Investment existing = new Investment();
        existing.setId(idGenerator.getAndIncrement());
        existing.setSymbol(originalSymbol);
        existing.setQuantity(originalQuantity);
        existing.setPlatform(originalPlatform);
        existing.setCreatedAt(OffsetDateTime.now().minusDays(1));
        store.put(existing.getId(), existing);

        Long investmentId = existing.getId();
        OffsetDateTime originalCreatedAt = existing.getCreatedAt();

        // --- Build a partial update request with only the selected fields ---
        InvestmentRequest updateRequest = new InvestmentRequest(
                fieldsToUpdate.contains("symbol") ? newSymbol : null,
                fieldsToUpdate.contains("quantity") ? newQuantity : null,
                fieldsToUpdate.contains("platform") ? newPlatform : null
        );

        // --- Perform the update ---
        Investment updated = investmentService.updateInvestment(investmentId, updateRequest);

        // --- Assert: id and createdAt are never changed ---
        assertThat(updated.getId()).isEqualTo(investmentId);
        assertThat(updated.getCreatedAt()).isEqualTo(originalCreatedAt);

        // --- Assert: only provided fields changed ---
        if (fieldsToUpdate.contains("symbol")) {
            assertThat(updated.getSymbol()).isEqualTo(newSymbol);
        } else {
            assertThat(updated.getSymbol()).isEqualTo(originalSymbol);
        }

        if (fieldsToUpdate.contains("quantity")) {
            assertThat(updated.getQuantity()).isEqualByComparingTo(newQuantity);
        } else {
            assertThat(updated.getQuantity()).isEqualByComparingTo(originalQuantity);
        }

        if (fieldsToUpdate.contains("platform")) {
            assertThat(updated.getPlatform()).isEqualTo(newPlatform);
        } else {
            assertThat(updated.getPlatform()).isEqualTo(originalPlatform);
        }
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
    Arbitrary<String> validPlatforms() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withChars(' ', '-', '_')
                .ofMinLength(1)
                .ofMaxLength(100);
    }

    @Provide
    Arbitrary<Set<String>> fieldSubsets() {
        // Generate a random non-empty subset of {symbol, quantity, platform}
        Set<String> allFields = Set.of("symbol", "quantity", "platform");

        return Arbitraries.of(allFields)
                .set()
                .ofMinSize(1)
                .ofMaxSize(3);
    }
}
