package com.investmenttracker.finnhub;

import com.investmenttracker.investment.Investment;
import com.investmenttracker.investment.InvestmentRepository;
import com.investmenttracker.investment.InvestmentRequest;
import com.investmenttracker.investment.InvestmentService;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based test for the subscription set invariant.
 *
 * <p><b>Feature: finnhub-websocket-api, Property 4: subscription set invariant across create and delete operations</b></p>
 *
 * <p><b>Validates: Requirements 5.1, 5.2, 5.3</b></p>
 *
 * <p>Generates random sequences of create/delete investment operations against an in-memory
 * mock repository and a mock {@link FinnhubClient}. After each operation, asserts that
 * {@link SubscriptionManager#getAll()} equals the set of distinct symbols with at least one
 * investment in the repository.</p>
 *
 * <p>Uses the normalized schema: multiple rows can share the same symbol (one per platform).
 * Unsubscription only happens when the last investment referencing a symbol is deleted.</p>
 */
class SubscriptionSetInvariantPropertyTest {

    private InvestmentRepository investmentRepository;
    private FinnhubClient finnhubClient;
    private SubscriptionManager subscriptionManager;
    private InvestmentService investmentService;

    // In-memory store simulating the database
    private Map<Long, Investment> store;
    private AtomicLong idGenerator;

    @BeforeProperty
    void setUp() {
        store = new HashMap<>();
        idGenerator = new AtomicLong(1);

        investmentRepository = mock(InvestmentRepository.class);
        finnhubClient = mock(FinnhubClient.class);

        // Use a REAL SubscriptionManager to test the actual invariant
        subscriptionManager = new SubscriptionManager();

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

        // Mock findDistinctSymbols: return distinct symbols from store
        when(investmentRepository.findDistinctSymbols()).thenAnswer(invocation ->
                store.values().stream()
                        .map(Investment::getSymbol)
                        .distinct()
                        .collect(Collectors.toList()));

        // Mock existsBySymbol: check if any investment in store has the symbol
        when(investmentRepository.existsBySymbol(anyString())).thenAnswer(invocation -> {
            String symbol = invocation.getArgument(0);
            return store.values().stream().anyMatch(inv -> inv.getSymbol().equals(symbol));
        });

        // Mock existsBySymbolAndIdNot: check if any other investment has the symbol
        when(investmentRepository.existsBySymbolAndIdNot(anyString(), anyLong())).thenAnswer(invocation -> {
            String symbol = invocation.getArgument(0);
            Long excludeId = invocation.getArgument(1);
            return store.values().stream()
                    .anyMatch(inv -> inv.getSymbol().equals(symbol) && !inv.getId().equals(excludeId));
        });

        investmentService = new InvestmentService(
                investmentRepository,
                subscriptionManager,
                finnhubClient
        );
    }

    @Property(tries = 100)
    @Label("Subscription set equals distinct symbols in repository after any sequence of create/delete operations")
    void subscriptionSetInvariantHoldsAcrossOperations(
            @ForAll("operationSequences") List<Operation> operations
    ) {
        // Simulate startup: initSubscriptions populates from empty DB
        investmentService.initSubscriptions();

        for (Operation op : operations) {
            if (op.isCreate()) {
                // In normalized schema, multiple rows per symbol are allowed.
                // Each create adds a new row (different platform).
                InvestmentRequest request = new InvestmentRequest(
                        op.symbol(),
                        new BigDecimal("1.00"),
                        op.platform()
                );
                investmentService.createInvestment(request);
            } else {
                // Delete: find any investment with this symbol and delete it
                Optional<Investment> toDelete = store.values().stream()
                        .filter(inv -> inv.getSymbol().equals(op.symbol()))
                        .findFirst();
                if (toDelete.isPresent()) {
                    investmentService.deleteInvestment(toDelete.get().getId());
                }
                // If no investment with this symbol exists, skip (no-op)
            }

            // INVARIANT: subscriptionManager.getAll() == distinct symbols in store
            Set<String> expectedSymbols = store.values().stream()
                    .map(Investment::getSymbol)
                    .collect(Collectors.toSet());

            assertThat(subscriptionManager.getAll())
                    .as("Subscription set should equal distinct symbols in repository")
                    .isEqualTo(expectedSymbols);
        }
    }

    // --- Operation model ---

    /**
     * Represents a single create or delete operation on an investment.
     */
    record Operation(String symbol, String platform, boolean isCreate) {
        static Operation create(String symbol, String platform) {
            return new Operation(symbol, platform, true);
        }

        static Operation delete(String symbol) {
            return new Operation(symbol, null, false);
        }
    }

    // --- Custom Arbitraries ---

    @Provide
    Arbitrary<List<Operation>> operationSequences() {
        // Use a small pool of symbols to increase the chance of collisions
        // (same symbol created/deleted multiple times)
        Arbitrary<String> symbols = Arbitraries.of("AAPL", "GOOG", "MSFT", "TSLA", "AMZN");
        Arbitrary<String> platforms = Arbitraries.of("Robinhood", "Coinbase", "401k", "Fidelity", "Schwab");

        Arbitrary<Operation> createOp = Combinators.combine(symbols, platforms)
                .as(Operation::create);
        Arbitrary<Operation> deleteOp = symbols.map(Operation::delete);

        // Mix creates and deletes with a bias toward creates (70/30)
        // to ensure there are usually investments to delete
        Arbitrary<Operation> operation = Arbitraries.frequencyOf(
                Tuple.of(7, createOp),
                Tuple.of(3, deleteOp)
        );

        return operation.list().ofMinSize(1).ofMaxSize(20);
    }
}
