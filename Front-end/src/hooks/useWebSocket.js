import { useState, useEffect, useRef } from 'react';

/**
 * WebSocket connection hook with exponential backoff reconnect logic.
 *
 * @param {string} url - WebSocket endpoint URL
 * @param {object} [options]
 * @param {function} [options.onMessage] - Callback invoked with parsed message data
 * @param {boolean} [options.reconnect=true] - Whether to auto-reconnect on non-1000 close
 * @param {number} [options.maxAttempts=10] - Maximum reconnection attempts
 * @returns {{ status: string, lastMessage: any, connect: function, disconnect: function }}
 */
export default function useWebSocket(url, options = {}) {
  const { onMessage, reconnect = true, maxAttempts = 10 } = options;

  const [status, setStatus] = useState('disconnected');
  const [lastMessage, setLastMessage] = useState(null);

  const wsRef = useRef(null);
  const attemptRef = useRef(0);
  const reconnectTimerRef = useRef(null);
  const intentionalCloseRef = useRef(false);
  const onMessageRef = useRef(onMessage);
  const urlRef = useRef(url);
  const reconnectRef = useRef(reconnect);
  const maxAttemptsRef = useRef(maxAttempts);

  // Keep refs in sync with latest values via effect (React Compiler requirement)
  useEffect(() => {
    onMessageRef.current = onMessage;
    urlRef.current = url;
    reconnectRef.current = reconnect;
    maxAttemptsRef.current = maxAttempts;
  });

  function computeDelay(attempt) {
    return Math.min(1000 * Math.pow(2, attempt - 1), 30000);
  }

  function connect() {
    // Clean up any existing connection
    if (wsRef.current) {
      intentionalCloseRef.current = true;
      const existing = wsRef.current;
      wsRef.current = null;
      // Only close if the socket is actually open or connecting
      if (existing.readyState === WebSocket.OPEN || existing.readyState === WebSocket.CONNECTING) {
        existing.close(1000);
      }
    }

    intentionalCloseRef.current = false;
    attemptRef.current = 0;
    createConnection();
  }

  function createConnection() {
    // Don't attempt connection if no token is available
    const token = localStorage.getItem('access_token');
    if (!token) {
      setStatus('disconnected');
      return;
    }

    try {
      const wsUrl = `${urlRef.current}?token=${token}`;
      const ws = new WebSocket(wsUrl);
      wsRef.current = ws;
      setStatus('connecting');

      ws.onopen = () => {
        // Ignore if this socket was already superseded
        if (wsRef.current !== ws) return;
        attemptRef.current = 0;
        setStatus('connected');
      };

      ws.onmessage = (event) => {
        if (wsRef.current !== ws) return;
        try {
          const data = JSON.parse(event.data);
          setLastMessage(data);
          if (onMessageRef.current) {
            onMessageRef.current(data);
          }
        } catch {
          console.warn('useWebSocket: received malformed JSON message, skipping');
        }
      };

      ws.onclose = (event) => {
        // Ignore close events from superseded sockets
        if (wsRef.current !== ws) return;
        wsRef.current = null;

        if (intentionalCloseRef.current || event.code === 1000) {
          setStatus('disconnected');
          return;
        }

        // Non-1000 close — attempt reconnect if enabled
        if (reconnectRef.current) {
          scheduleReconnect();
        } else {
          setStatus('disconnected');
        }
      };

      ws.onerror = () => {
        // The close event will fire after this, which handles reconnection
      };
    } catch {
      // Connection creation failed — attempt reconnect
      if (reconnectRef.current) {
        scheduleReconnect();
      } else {
        setStatus('disconnected');
      }
    }
  }

  function scheduleReconnect() {
    attemptRef.current += 1;

    if (attemptRef.current > maxAttemptsRef.current) {
      setStatus('failed');
      return;
    }

    setStatus('reconnecting');
    const delay = computeDelay(attemptRef.current);

    reconnectTimerRef.current = setTimeout(() => {
      reconnectTimerRef.current = null;
      createConnection();
    }, delay);
  }

  function disconnect() {
    intentionalCloseRef.current = true;

    if (reconnectTimerRef.current) {
      clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
    }

    if (wsRef.current) {
      const ws = wsRef.current;
      wsRef.current = null;
      // Only attempt close if socket is open or connecting
      if (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING) {
        ws.close(1000);
      }
    }

    setStatus('disconnected');
  }

  useEffect(() => {
    connect();

    return () => {
      disconnect();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return { status, lastMessage, connect, disconnect };
}
