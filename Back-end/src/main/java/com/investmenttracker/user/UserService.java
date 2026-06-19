package com.investmenttracker.user;

import com.investmenttracker.finnhub.FinnhubClient;
import com.investmenttracker.finnhub.SubscriptionManager;
import com.investmenttracker.investment.Holding;
import com.investmenttracker.investment.HoldingRepository;
import com.investmenttracker.websocket.SessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service responsible for user account operations including deletion.
 *
 * Coordinates between the persistence layer, subscription management,
 * and WebSocket session registry to ensure complete cleanup when a user
 * account is deleted.
 *
 * Requirements: 8.4, 8.5
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final HoldingRepository holdingRepository;
    private final SubscriptionManager subscriptionManager;
    private final FinnhubClient finnhubClient;
    private final SessionRegistry sessionRegistry;

    public UserService(UserRepository userRepository,
                       HoldingRepository holdingRepository,
                       SubscriptionManager subscriptionManager,
                       FinnhubClient finnhubClient,
                       SessionRegistry sessionRegistry) {
        this.userRepository = userRepository;
        this.holdingRepository = holdingRepository;
        this.subscriptionManager = subscriptionManager;
        this.finnhubClient = finnhubClient;
        this.sessionRegistry = sessionRegistry;
    }

    /**
     * Permanently deletes a user account and all associated data.
     *
     * This method:
     * 1. Retrieves all holdings for the user
     * 2. Identifies distinct symbols held by the user
     * 3. Deletes all holdings for the user
     * 4. For each symbol no longer referenced by any holding, unsubscribes from Finnhub
     * 5. Removes user's symbols from their WebSocket sessions
     * 6. Deletes the user record
     *
     * All operations run within a single transaction boundary.
     *
     * @param user the user to delete (must be a managed entity)
     *
     * Requirements: 8.4, 8.5
     */
    @Transactional
    public void deleteUser(User user) {
        log.info("UserService: deleting user id={}, username='{}'", user.getId(), user.getUsername());

        // 1. Get all holdings for the user
        List<Holding> holdings = holdingRepository.findByUser(user);

        // 2. Identify distinct symbols the user holds
        Set<String> userSymbols = holdings.stream()
                .map(h -> h.getSymbol().getTicker())
                .collect(Collectors.toSet());

        // 3. Delete all holdings for the user
        holdingRepository.deleteAll(holdings);

        // 4. For each symbol, check if it's still referenced by other users' holdings.
        //    If not, unsubscribe from Finnhub and remove from subscription manager.
        for (String ticker : userSymbols) {
            boolean stillReferenced = holdingRepository.existsBySymbol_Ticker(ticker);
            if (!stillReferenced) {
                subscriptionManager.remove(ticker);
                finnhubClient.unsubscribe(ticker);
                log.info("UserService: unsubscribed symbol='{}' (no longer referenced)", ticker);
            }
        }

        // 5. Remove symbols from the user's WebSocket sessions
        for (String ticker : userSymbols) {
            sessionRegistry.removeSymbolFromUserSessions(user, ticker);
        }

        // 6. Delete the user record
        userRepository.delete(user);

        log.info("UserService: successfully deleted user id={}, removed {} holding(s)",
                user.getId(), holdings.size());
    }
}
