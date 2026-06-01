package com.investmenttracker.investment;

import com.investmenttracker.finnhub.FinnhubClient;
import com.investmenttracker.finnhub.SubscriptionManager;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link InvestmentService}.
 *
 * <p>Tests are plain JUnit 5 + Mockito — no Spring context required.
 * Dependencies (InvestmentRepository, SubscriptionManager, FinnhubClient)
 * are mocked to isolate the service logic.
 *
 * Requirements: 5.1, 5.2, 7.3
 */
@ExtendWith(MockitoExtension.class)
class InvestmentServiceTest {

    @Mock
    private InvestmentRepository investmentRepository;

    @Mock
    private SubscriptionManager subscriptionManager;

    @Mock
    private FinnhubClient finnhubClient;

    private InvestmentService investmentService;

    @BeforeEach
    void setUp() {
        investmentService = new InvestmentService(
                investmentRepository,
                subscriptionManager,
                finnhubClient
        );
    }

    // -------------------------------------------------------------------------
    // createInvestment — Requirement 5.1
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("createInvestment")
    class CreateInvestment {

        @Test
        @DisplayName("subscribes to symbol when it is new (not already in SubscriptionManager)")
        void createInvestment_newSymbol_subscribes() {
            // Arrange
            InvestmentRequest request = new InvestmentRequest("AAPL", new BigDecimal("10.5"), "Robinhood");

            Investment saved = buildInvestment(1L, "AAPL", new BigDecimal("10.5"), "Robinhood");
            when(investmentRepository.save(any(Investment.class))).thenReturn(saved);
            when(subscriptionManager.add("AAPL")).thenReturn(true); // symbol is new

            // Act
            Investment result = investmentService.createInvestment(request);

            // Assert
            assertThat(result.getSymbol()).isEqualTo("AAPL");
            verify(subscriptionManager).add("AAPL");
            verify(finnhubClient).subscribe("AAPL");
        }

        @Test
        @DisplayName("does NOT subscribe when symbol is already in SubscriptionManager")
        void createInvestment_existingSymbol_doesNotSubscribe() {
            // Arrange
            InvestmentRequest request = new InvestmentRequest("AAPL", new BigDecimal("5.0"), "Coinbase");

            Investment saved = buildInvestment(2L, "AAPL", new BigDecimal("5.0"), "Coinbase");
            when(investmentRepository.save(any(Investment.class))).thenReturn(saved);
            when(subscriptionManager.add("AAPL")).thenReturn(false); // symbol already tracked

            // Act
            Investment result = investmentService.createInvestment(request);

            // Assert
            assertThat(result.getSymbol()).isEqualTo("AAPL");
            verify(subscriptionManager).add("AAPL");
            verify(finnhubClient, never()).subscribe(anyString());
        }

        @Test
        @DisplayName("persists the investment to the repository")
        void createInvestment_persistsToRepository() {
            // Arrange
            InvestmentRequest request = new InvestmentRequest("GOOG", new BigDecimal("2.0"), null);

            Investment saved = buildInvestment(3L, "GOOG", new BigDecimal("2.0"), null);
            when(investmentRepository.save(any(Investment.class))).thenReturn(saved);
            when(subscriptionManager.add("GOOG")).thenReturn(true);

            // Act
            Investment result = investmentService.createInvestment(request);

            // Assert
            verify(investmentRepository).save(any(Investment.class));
            assertThat(result.getId()).isEqualTo(3L);
        }
    }

    // -------------------------------------------------------------------------
    // deleteInvestment — Requirement 5.2
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("deleteInvestment")
    class DeleteInvestment {

        @Test
        @DisplayName("unsubscribes from symbol when no other investment references it")
        void deleteInvestment_orphanedSymbol_unsubscribes() {
            // Arrange
            Investment existing = buildInvestment(1L, "AAPL", new BigDecimal("10.0"), "Robinhood");
            when(investmentRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(investmentRepository.existsBySymbol("AAPL")).thenReturn(false); // no other references

            // Act
            investmentService.deleteInvestment(1L);

            // Assert
            verify(investmentRepository).delete(existing);
            verify(subscriptionManager).remove("AAPL");
            verify(finnhubClient).unsubscribe("AAPL");
        }

        @Test
        @DisplayName("does NOT unsubscribe when another investment still references the symbol")
        void deleteInvestment_symbolStillReferenced_doesNotUnsubscribe() {
            // Arrange
            Investment existing = buildInvestment(1L, "AAPL", new BigDecimal("10.0"), "Robinhood");
            when(investmentRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(investmentRepository.existsBySymbol("AAPL")).thenReturn(true); // still referenced

            // Act
            investmentService.deleteInvestment(1L);

            // Assert
            verify(investmentRepository).delete(existing);
            verify(subscriptionManager, never()).remove(anyString());
            verify(finnhubClient, never()).unsubscribe(anyString());
        }

        @Test
        @DisplayName("throws EntityNotFoundException when investment ID does not exist")
        void deleteInvestment_nonExistentId_throwsException() {
            // Arrange
            when(investmentRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> investmentService.deleteInvestment(99L))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("99");

            verify(finnhubClient, never()).unsubscribe(anyString());
            verify(subscriptionManager, never()).remove(anyString());
        }
    }

    // -------------------------------------------------------------------------
    // updateInvestment — Requirement 7.3 (symbol change handling)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("updateInvestment")
    class UpdateInvestment {

        @Test
        @DisplayName("unsubscribes old symbol and subscribes new symbol on symbol change when old is orphaned")
        void updateInvestment_symbolChange_unsubscribesOldAndSubscribesNew() {
            // Arrange
            Investment existing = buildInvestment(1L, "AAPL", new BigDecimal("10.0"), "Robinhood");
            when(investmentRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(investmentRepository.save(any(Investment.class))).thenAnswer(inv -> inv.getArgument(0));
            when(investmentRepository.existsBySymbolAndIdNot("AAPL", 1L)).thenReturn(false); // old symbol orphaned
            when(subscriptionManager.add("GOOG")).thenReturn(true); // new symbol not yet tracked

            InvestmentRequest request = new InvestmentRequest("GOOG", null, null);

            // Act
            Investment result = investmentService.updateInvestment(1L, request);

            // Assert
            assertThat(result.getSymbol()).isEqualTo("GOOG");
            verify(subscriptionManager).remove("AAPL");
            verify(finnhubClient).unsubscribe("AAPL");
            verify(subscriptionManager).add("GOOG");
            verify(finnhubClient).subscribe("GOOG");
        }

        @Test
        @DisplayName("does NOT unsubscribe old symbol when another investment still references it")
        void updateInvestment_symbolChange_oldStillReferenced_doesNotUnsubscribe() {
            // Arrange
            Investment existing = buildInvestment(1L, "AAPL", new BigDecimal("10.0"), "Robinhood");
            when(investmentRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(investmentRepository.save(any(Investment.class))).thenAnswer(inv -> inv.getArgument(0));
            when(investmentRepository.existsBySymbolAndIdNot("AAPL", 1L)).thenReturn(true); // old symbol still referenced
            when(subscriptionManager.add("GOOG")).thenReturn(true);

            InvestmentRequest request = new InvestmentRequest("GOOG", null, null);

            // Act
            Investment result = investmentService.updateInvestment(1L, request);

            // Assert
            assertThat(result.getSymbol()).isEqualTo("GOOG");
            verify(subscriptionManager, never()).remove("AAPL");
            verify(finnhubClient, never()).unsubscribe("AAPL");
            verify(subscriptionManager).add("GOOG");
            verify(finnhubClient).subscribe("GOOG");
        }

        @Test
        @DisplayName("does NOT subscribe new symbol if already tracked")
        void updateInvestment_symbolChangeToExistingSymbol_doesNotSubscribeNew() {
            // Arrange
            Investment existing = buildInvestment(1L, "AAPL", new BigDecimal("10.0"), "Robinhood");
            when(investmentRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(investmentRepository.save(any(Investment.class))).thenAnswer(inv -> inv.getArgument(0));
            when(investmentRepository.existsBySymbolAndIdNot("AAPL", 1L)).thenReturn(false);
            when(subscriptionManager.add("GOOG")).thenReturn(false); // new symbol already tracked

            InvestmentRequest request = new InvestmentRequest("GOOG", null, null);

            // Act
            Investment result = investmentService.updateInvestment(1L, request);

            // Assert
            assertThat(result.getSymbol()).isEqualTo("GOOG");
            verify(subscriptionManager).remove("AAPL");
            verify(finnhubClient).unsubscribe("AAPL");
            verify(subscriptionManager).add("GOOG");
            verify(finnhubClient, never()).subscribe(anyString());
        }

        @Test
        @DisplayName("does NOT unsubscribe or subscribe when symbol is unchanged")
        void updateInvestment_noSymbolChange_noSubscriptionChanges() {
            // Arrange
            Investment existing = buildInvestment(1L, "AAPL", new BigDecimal("10.0"), "Robinhood");
            when(investmentRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(investmentRepository.save(any(Investment.class))).thenAnswer(inv -> inv.getArgument(0));

            InvestmentRequest request = new InvestmentRequest(null, new BigDecimal("20.0"), null);

            // Act
            Investment result = investmentService.updateInvestment(1L, request);

            // Assert
            assertThat(result.getQuantity()).isEqualByComparingTo(new BigDecimal("20.0"));
            verify(subscriptionManager, never()).remove(anyString());
            verify(subscriptionManager, never()).add(anyString());
            verify(finnhubClient, never()).subscribe(anyString());
            verify(finnhubClient, never()).unsubscribe(anyString());
        }

        @Test
        @DisplayName("updates only provided fields (partial update)")
        void updateInvestment_partialUpdate_onlyChangesProvidedFields() {
            // Arrange
            Investment existing = buildInvestment(1L, "AAPL", new BigDecimal("10.0"), "Robinhood");
            when(investmentRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(investmentRepository.save(any(Investment.class))).thenAnswer(inv -> inv.getArgument(0));

            // Only update quantity
            InvestmentRequest request = new InvestmentRequest(null, new BigDecimal("25.0"), null);

            // Act
            Investment result = investmentService.updateInvestment(1L, request);

            // Assert — quantity changed, symbol and platform unchanged
            assertThat(result.getSymbol()).isEqualTo("AAPL");
            assertThat(result.getQuantity()).isEqualByComparingTo(new BigDecimal("25.0"));
            assertThat(result.getPlatform()).isEqualTo("Robinhood");
        }

        @Test
        @DisplayName("throws EntityNotFoundException when investment ID does not exist")
        void updateInvestment_nonExistentId_throwsException() {
            // Arrange
            when(investmentRepository.findById(99L)).thenReturn(Optional.empty());

            InvestmentRequest request = new InvestmentRequest("GOOG", null, null);

            // Act & Assert
            assertThatThrownBy(() -> investmentService.updateInvestment(99L, request))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("99");

            verify(finnhubClient, never()).subscribe(anyString());
            verify(finnhubClient, never()).unsubscribe(anyString());
        }
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private Investment buildInvestment(Long id, String symbol, BigDecimal quantity, String platform) {
        Investment investment = new Investment();
        investment.setId(id);
        investment.setSymbol(symbol);
        investment.setQuantity(quantity);
        investment.setPlatform(platform);
        return investment;
    }
}
