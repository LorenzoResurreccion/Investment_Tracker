package com.investmenttracker.investment;

import com.investmenttracker.finnhub.FinnhubClient;
import com.investmenttracker.finnhub.SubscriptionManager;
import com.investmenttracker.symbol.Symbol;
import com.investmenttracker.symbol.SymbolRepository;
import com.investmenttracker.user.User;
import com.investmenttracker.websocket.SessionRegistry;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link HoldingService}.
 *
 * Tests are plain JUnit 5 + Mockito — no Spring context required.
 * Dependencies are mocked to isolate the service logic.
 *
 * Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 5.5, 5.6
 */
@ExtendWith(MockitoExtension.class)
class HoldingServiceTest {

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private SymbolRepository symbolRepository;

    @Mock
    private SubscriptionManager subscriptionManager;

    @Mock
    private FinnhubClient finnhubClient;

    @Mock
    private SessionRegistry sessionRegistry;

    private HoldingService holdingService;

    private User userA;
    private User userB;
    private Symbol appleSymbol;

    @BeforeEach
    void setUp() {
        holdingService = new HoldingService(
                holdingRepository,
                symbolRepository,
                subscriptionManager,
                finnhubClient,
                sessionRegistry
        );

        userA = new User();
        userA.setId(1L);
        userA.setUsername("alice");
        userA.setEmail("alice@example.com");
        userA.setCognitoSub("sub-alice");

        userB = new User();
        userB.setId(2L);
        userB.setUsername("bob");
        userB.setEmail("bob@example.com");
        userB.setCognitoSub("sub-bob");

        appleSymbol = new Symbol();
        appleSymbol.setId(1L);
        appleSymbol.setTicker("AAPL");
    }

    // -------------------------------------------------------------------------
    // getUserHoldings — Requirement 4.1
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getUserHoldings")
    class GetUserHoldings {

        @Test
        @DisplayName("returns only the authenticated user's holdings")
        void returnsOnlyUserHoldings() {
            Holding h1 = buildHolding(1L, userA, appleSymbol, new BigDecimal("10"), "Robinhood");
            when(holdingRepository.findByUser(userA)).thenReturn(List.of(h1));

            List<Holding> result = holdingService.getUserHoldings(userA);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUser()).isEqualTo(userA);
            verify(holdingRepository).findByUser(userA);
        }

        @Test
        @DisplayName("returns empty list when user has no holdings")
        void returnsEmptyForUserWithNoHoldings() {
            when(holdingRepository.findByUser(userB)).thenReturn(List.of());

            List<Holding> result = holdingService.getUserHoldings(userB);

            assertThat(result).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // getPortfolioSummary — Requirement 4.1, 4.2
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getPortfolioSummary")
    class GetPortfolioSummary {

        @Test
        @DisplayName("aggregates holdings by symbol for the user")
        void aggregatesBySymbol() {
            Symbol googSymbol = new Symbol();
            googSymbol.setId(2L);
            googSymbol.setTicker("GOOG");

            Holding h1 = buildHolding(1L, userA, appleSymbol, new BigDecimal("10"), "Robinhood");
            h1.setAverageCost(new BigDecimal("150.00"));
            Holding h2 = buildHolding(2L, userA, appleSymbol, new BigDecimal("5"), "Fidelity");
            h2.setAverageCost(new BigDecimal("160.00"));
            Holding h3 = buildHolding(3L, userA, googSymbol, new BigDecimal("2"), "Robinhood");
            h3.setAverageCost(new BigDecimal("2800.00"));

            when(holdingRepository.findByUser(userA)).thenReturn(List.of(h1, h2, h3));

            List<PortfolioSummaryResponse> summary = holdingService.getPortfolioSummary(userA);

            assertThat(summary).hasSize(2);
            // Sorted alphabetically: AAPL first, then GOOG
            assertThat(summary.get(0).symbol()).isEqualTo("AAPL");
            assertThat(summary.get(0).totalQuantity()).isEqualByComparingTo(new BigDecimal("15"));
            assertThat(summary.get(0).holdingCount()).isEqualTo(2);
            assertThat(summary.get(1).symbol()).isEqualTo("GOOG");
            assertThat(summary.get(1).totalQuantity()).isEqualByComparingTo(new BigDecimal("2"));
            assertThat(summary.get(1).holdingCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("returns empty list when user has no holdings")
        void returnsEmptyForNoHoldings() {
            when(holdingRepository.findByUser(userA)).thenReturn(List.of());

            List<PortfolioSummaryResponse> summary = holdingService.getPortfolioSummary(userA);

            assertThat(summary).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // getHoldingsBySymbol — Requirement 4.2
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getHoldingsBySymbol")
    class GetHoldingsBySymbol {

        @Test
        @DisplayName("returns holdings for user filtered by symbol ticker")
        void returnsFilteredHoldings() {
            Holding h1 = buildHolding(1L, userA, appleSymbol, new BigDecimal("10"), "Robinhood");
            when(holdingRepository.findByUserAndSymbol_Ticker(userA, "AAPL")).thenReturn(List.of(h1));

            List<Holding> result = holdingService.getHoldingsBySymbol(userA, "AAPL");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSymbol().getTicker()).isEqualTo("AAPL");
        }

        @Test
        @DisplayName("returns empty list when user has no holdings for the symbol")
        void returnsEmptyForNonHeldSymbol() {
            when(holdingRepository.findByUserAndSymbol_Ticker(userA, "TSLA")).thenReturn(List.of());

            List<Holding> result = holdingService.getHoldingsBySymbol(userA, "TSLA");

            assertThat(result).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // createHolding — Requirement 4.3, 5.5
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("createHolding")
    class CreateHolding {

        @Test
        @DisplayName("creates holding with existing symbol and subscribes when new")
        void createsWithExistingSymbol_subscribes() {
            InvestmentRequest request = new InvestmentRequest("AAPL", new BigDecimal("10"), "Robinhood");
            when(symbolRepository.findByTicker("AAPL")).thenReturn(Optional.of(appleSymbol));
            when(holdingRepository.save(any(Holding.class))).thenAnswer(inv -> {
                Holding h = inv.getArgument(0);
                h.setId(1L);
                return h;
            });
            when(subscriptionManager.add("AAPL")).thenReturn(true);

            Holding result = holdingService.createHolding(userA, request);

            assertThat(result.getUser()).isEqualTo(userA);
            assertThat(result.getSymbol()).isEqualTo(appleSymbol);
            assertThat(result.getQuantity()).isEqualByComparingTo(new BigDecimal("10"));
            verify(finnhubClient).subscribe("AAPL");
        }

        @Test
        @DisplayName("auto-creates Symbol if it doesn't exist")
        void autoCreatesSymbol() {
            InvestmentRequest request = new InvestmentRequest("TSLA", new BigDecimal("5"), "Fidelity");
            when(symbolRepository.findByTicker("TSLA")).thenReturn(Optional.empty());
            Symbol newSymbol = new Symbol();
            newSymbol.setId(99L);
            newSymbol.setTicker("TSLA");
            when(symbolRepository.save(any(Symbol.class))).thenReturn(newSymbol);
            when(holdingRepository.save(any(Holding.class))).thenAnswer(inv -> {
                Holding h = inv.getArgument(0);
                h.setId(2L);
                return h;
            });
            when(subscriptionManager.add("TSLA")).thenReturn(true);

            Holding result = holdingService.createHolding(userA, request);

            assertThat(result.getSymbol().getTicker()).isEqualTo("TSLA");
            verify(symbolRepository).save(any(Symbol.class));
            verify(finnhubClient).subscribe("TSLA");
        }

        @Test
        @DisplayName("does NOT subscribe when symbol already tracked")
        void doesNotSubscribeWhenAlreadyTracked() {
            InvestmentRequest request = new InvestmentRequest("AAPL", new BigDecimal("10"), "Robinhood");
            when(symbolRepository.findByTicker("AAPL")).thenReturn(Optional.of(appleSymbol));
            when(holdingRepository.save(any(Holding.class))).thenAnswer(inv -> {
                Holding h = inv.getArgument(0);
                h.setId(1L);
                return h;
            });
            when(subscriptionManager.add("AAPL")).thenReturn(false);

            holdingService.createHolding(userA, request);

            verify(finnhubClient, never()).subscribe(anyString());
        }
    }

    // -------------------------------------------------------------------------
    // updateHolding — Requirement 4.4, 4.5, 5.5, 5.6
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("updateHolding")
    class UpdateHolding {

        @Test
        @DisplayName("updates holding fields when user owns it")
        void updatesOwnedHolding() {
            Holding existing = buildHolding(1L, userA, appleSymbol, new BigDecimal("10"), "Robinhood");
            when(holdingRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(holdingRepository.save(any(Holding.class))).thenAnswer(inv -> inv.getArgument(0));

            InvestmentRequest request = new InvestmentRequest(null, new BigDecimal("20"), null);

            Holding result = holdingService.updateHolding(userA, 1L, request);

            assertThat(result.getQuantity()).isEqualByComparingTo(new BigDecimal("20"));
            assertThat(result.getSymbol().getTicker()).isEqualTo("AAPL");
        }

        @Test
        @DisplayName("throws AccessDeniedException when user does not own the holding")
        void throwsWhenNotOwner() {
            Holding existing = buildHolding(1L, userA, appleSymbol, new BigDecimal("10"), "Robinhood");
            when(holdingRepository.findById(1L)).thenReturn(Optional.of(existing));

            InvestmentRequest request = new InvestmentRequest(null, new BigDecimal("20"), null);

            assertThatThrownBy(() -> holdingService.updateHolding(userB, 1L, request))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Cannot modify another user's holding");

            verify(holdingRepository, never()).save(any());
        }

        @Test
        @DisplayName("handles symbol change — unsubscribes old if orphaned, subscribes new")
        void handlesSymbolChange() {
            Holding existing = buildHolding(1L, userA, appleSymbol, new BigDecimal("10"), "Robinhood");
            when(holdingRepository.findById(1L)).thenReturn(Optional.of(existing));

            Symbol googSymbol = new Symbol();
            googSymbol.setId(2L);
            googSymbol.setTicker("GOOG");
            when(symbolRepository.findByTicker("GOOG")).thenReturn(Optional.of(googSymbol));
            when(holdingRepository.save(any(Holding.class))).thenAnswer(inv -> inv.getArgument(0));
            when(holdingRepository.existsBySymbol_Ticker("AAPL")).thenReturn(false);
            when(subscriptionManager.add("GOOG")).thenReturn(true);

            InvestmentRequest request = new InvestmentRequest("GOOG", null, null);

            Holding result = holdingService.updateHolding(userA, 1L, request);

            assertThat(result.getSymbol().getTicker()).isEqualTo("GOOG");
            verify(subscriptionManager).remove("AAPL");
            verify(finnhubClient).unsubscribe("AAPL");
            verify(subscriptionManager).add("GOOG");
            verify(finnhubClient).subscribe("GOOG");
        }

        @Test
        @DisplayName("does NOT unsubscribe old symbol when still referenced by other holdings")
        void doesNotUnsubscribeWhenStillReferenced() {
            Holding existing = buildHolding(1L, userA, appleSymbol, new BigDecimal("10"), "Robinhood");
            when(holdingRepository.findById(1L)).thenReturn(Optional.of(existing));

            Symbol googSymbol = new Symbol();
            googSymbol.setId(2L);
            googSymbol.setTicker("GOOG");
            when(symbolRepository.findByTicker("GOOG")).thenReturn(Optional.of(googSymbol));
            when(holdingRepository.save(any(Holding.class))).thenAnswer(inv -> inv.getArgument(0));
            when(holdingRepository.existsBySymbol_Ticker("AAPL")).thenReturn(true);
            when(subscriptionManager.add("GOOG")).thenReturn(true);

            InvestmentRequest request = new InvestmentRequest("GOOG", null, null);

            holdingService.updateHolding(userA, 1L, request);

            verify(subscriptionManager, never()).remove("AAPL");
            verify(finnhubClient, never()).unsubscribe("AAPL");
        }

        @Test
        @DisplayName("throws EntityNotFoundException when holding ID does not exist")
        void throwsWhenNotFound() {
            when(holdingRepository.findById(99L)).thenReturn(Optional.empty());

            InvestmentRequest request = new InvestmentRequest("GOOG", null, null);

            assertThatThrownBy(() -> holdingService.updateHolding(userA, 99L, request))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    // -------------------------------------------------------------------------
    // deleteHolding — Requirement 4.5, 4.6, 5.6
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("deleteHolding")
    class DeleteHolding {

        @Test
        @DisplayName("deletes holding and unsubscribes when symbol is orphaned")
        void deletesAndUnsubscribes() {
            Holding existing = buildHolding(1L, userA, appleSymbol, new BigDecimal("10"), "Robinhood");
            when(holdingRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(holdingRepository.existsBySymbol_Ticker("AAPL")).thenReturn(false);

            holdingService.deleteHolding(userA, 1L);

            verify(holdingRepository).delete(existing);
            verify(subscriptionManager).remove("AAPL");
            verify(finnhubClient).unsubscribe("AAPL");
        }

        @Test
        @DisplayName("deletes holding but does NOT unsubscribe when symbol still referenced")
        void deletesButDoesNotUnsubscribe() {
            Holding existing = buildHolding(1L, userA, appleSymbol, new BigDecimal("10"), "Robinhood");
            when(holdingRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(holdingRepository.existsBySymbol_Ticker("AAPL")).thenReturn(true);

            holdingService.deleteHolding(userA, 1L);

            verify(holdingRepository).delete(existing);
            verify(subscriptionManager, never()).remove(anyString());
            verify(finnhubClient, never()).unsubscribe(anyString());
        }

        @Test
        @DisplayName("throws AccessDeniedException when user does not own the holding")
        void throwsWhenNotOwner() {
            Holding existing = buildHolding(1L, userA, appleSymbol, new BigDecimal("10"), "Robinhood");
            when(holdingRepository.findById(1L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> holdingService.deleteHolding(userB, 1L))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Cannot delete another user's holding");

            verify(holdingRepository, never()).delete(any());
        }

        @Test
        @DisplayName("throws EntityNotFoundException when holding ID does not exist")
        void throwsWhenNotFound() {
            when(holdingRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> holdingService.deleteHolding(userA, 99L))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private Holding buildHolding(Long id, User user, Symbol symbol, BigDecimal quantity, String platform) {
        Holding holding = new Holding();
        holding.setId(id);
        holding.setUser(user);
        holding.setSymbol(symbol);
        holding.setQuantity(quantity);
        holding.setPlatform(platform);
        return holding;
    }
}
