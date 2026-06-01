package com.investmenttracker.websocket;

import jakarta.websocket.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PriceWebSocketEndpoint}.
 * Verifies session management on open, close, and error events.
 */
class PriceWebSocketEndpointTest {

    private PriceWebSocketEndpoint endpoint;

    @BeforeEach
    void setUp() {
        // Clear any sessions from previous tests by closing/removing them
        for (Session s : PriceWebSocketEndpoint.getSessions()) {
            endpoint = new PriceWebSocketEndpoint();
            endpoint.onClose(s);
        }
        endpoint = new PriceWebSocketEndpoint();
    }

    @Test
    void onOpen_addsSessionToActiveSet() {
        Session session = mockSession("session-1");

        endpoint.onOpen(session);

        assertThat(PriceWebSocketEndpoint.getSessions()).contains(session);
    }

    @Test
    void onClose_removesSessionFromActiveSet() {
        Session session = mockSession("session-2");
        endpoint.onOpen(session);

        endpoint.onClose(session);

        assertThat(PriceWebSocketEndpoint.getSessions()).doesNotContain(session);
    }

    @Test
    void onError_removesSessionFromActiveSet() {
        Session session = mockSession("session-3");
        endpoint.onOpen(session);

        endpoint.onError(session, new RuntimeException("connection reset"));

        assertThat(PriceWebSocketEndpoint.getSessions()).doesNotContain(session);
    }

    @Test
    void getSessions_returnsUnmodifiableView() {
        Session session = mockSession("session-4");
        endpoint.onOpen(session);

        assertThat(PriceWebSocketEndpoint.getSessions()).isNotEmpty();
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> PriceWebSocketEndpoint.getSessions().add(mock(Session.class))
        );
    }

    @Test
    void multipleSessionsTrackedCorrectly() {
        Session session1 = mockSession("session-5");
        Session session2 = mockSession("session-6");

        endpoint.onOpen(session1);
        endpoint.onOpen(session2);

        assertThat(PriceWebSocketEndpoint.getSessions()).containsExactlyInAnyOrder(session1, session2);

        endpoint.onClose(session1);

        assertThat(PriceWebSocketEndpoint.getSessions()).containsExactly(session2);
    }

    private Session mockSession(String id) {
        Session session = mock(Session.class);
        when(session.getId()).thenReturn(id);
        return session;
    }
}
