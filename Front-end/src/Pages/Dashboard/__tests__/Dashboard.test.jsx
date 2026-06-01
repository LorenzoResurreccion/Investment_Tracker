/**
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import Dashboard from '../Dashboard.jsx';

// --- Mock child components ---
vi.mock('../Charts/StockPieChart.jsx', () => ({
  default: (props) => (
    <div data-testid="stock-pie-chart" data-summary={JSON.stringify(props.summary)} data-pricemap={JSON.stringify(props.priceMap)}>
      StockPieChart
    </div>
  ),
}));

vi.mock('../Charts/PortfolioValueGraph.jsx', () => ({
  default: (props) => (
    <div data-testid="portfolio-value-graph" data-datapoints={JSON.stringify(props.dataPoints)} data-currenttotal={props.currentTotal}>
      PortfolioValueGraph
    </div>
  ),
}));

vi.mock('../Stocks/StocksList.jsx', () => ({
  default: (props) => (
    <div data-testid="stocks-list" data-summary={JSON.stringify(props.summary)} data-pricemap={JSON.stringify(props.priceMap)}>
      StocksList
    </div>
  ),
}));

vi.mock('../Stocks/AddStockButton.jsx', () => ({
  default: (props) => (
    <div data-testid="add-stock-button">
      <button onClick={props.onClick}>Add Stock</button>
    </div>
  ),
}));

vi.mock('../Stocks/AddStockForm.jsx', () => ({
  default: () => <div data-testid="add-stock-form">AddStockForm</div>,
}));

vi.mock('../Status/ConnectionIndicator.jsx', () => ({
  default: (props) => (
    <div data-testid="connection-indicator" data-status={props.status}>
      ConnectionIndicator
    </div>
  ),
}));

vi.mock('../Status/DashboardSkeleton.jsx', () => ({
  default: () => <div data-testid="dashboard-skeleton">DashboardSkeleton</div>,
}));

// --- Mock hooks ---
let mockApiGet;
let mockWsOnMessage;
let mockWsStatus;
let mockDisconnect;

vi.mock('../../../hooks/useApi.js', () => ({
  default: () => ({
    get: (...args) => mockApiGet(...args),
    post: vi.fn(),
    put: vi.fn(),
    del: vi.fn(),
  }),
}));

vi.mock('../../../hooks/useWebSocket.js', () => ({
  default: (url, options) => {
    // Capture the onMessage callback so tests can invoke it
    mockWsOnMessage = options?.onMessage;
    return {
      status: mockWsStatus,
      lastMessage: null,
      connect: vi.fn(),
      disconnect: mockDisconnect,
    };
  },
}));

// --- Test setup ---
beforeEach(() => {
  mockWsStatus = 'connected';
  mockDisconnect = vi.fn();
  mockApiGet = vi.fn();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('Dashboard', () => {
  describe('Loading state (Req 9.1)', () => {
    it('renders DashboardSkeleton while initial fetch is pending', () => {
      // API never resolves — stays in loading state
      mockApiGet.mockReturnValue(new Promise(() => {}));

      render(<Dashboard />);

      expect(screen.getByTestId('dashboard-skeleton')).toBeTruthy();
      expect(screen.queryByTestId('stock-pie-chart')).toBeNull();
      expect(screen.queryByTestId('portfolio-value-graph')).toBeNull();
      expect(screen.queryByTestId('stocks-list')).toBeNull();
    });
  });

  describe('Error state (Req 9.3)', () => {
    it('renders full-page error with retry button on fetch failure', async () => {
      mockApiGet.mockResolvedValue({ data: null, error: 'Network error', status: null });

      await act(async () => {
        render(<Dashboard />);
      });

      expect(screen.getByText('Network error')).toBeTruthy();
      expect(screen.getByRole('button', { name: /retry/i })).toBeTruthy();
      expect(screen.queryByTestId('dashboard-skeleton')).toBeNull();
      expect(screen.queryByTestId('stock-pie-chart')).toBeNull();
    });

    it('retries fetch when retry button is clicked', async () => {
      // First call fails, second succeeds
      mockApiGet
        .mockResolvedValueOnce({ data: null, error: 'Server error', status: 500 })
        .mockResolvedValueOnce({ data: [], error: null, status: 200 });

      await act(async () => {
        render(<Dashboard />);
      });

      expect(screen.getByText('Server error')).toBeTruthy();

      await act(async () => {
        screen.getByRole('button', { name: /retry/i }).click();
      });

      // After retry succeeds, dashboard content should render
      expect(screen.queryByText('Server error')).toBeNull();
      expect(screen.getByTestId('stock-pie-chart')).toBeTruthy();
    });
  });

  describe('Layout order (Req 1.1, 1.2, 1.3)', () => {
    it('renders components in correct order: PieChart → Graph → AddStockButton → StocksList', async () => {
      mockApiGet.mockResolvedValue({
        data: [{ symbol: 'AAPL', totalQuantity: 10, holdingCount: 1 }],
        error: null,
        status: 200,
      });

      let container;
      await act(async () => {
        const result = render(<Dashboard />);
        container = result.container;
      });

      const pieChart = screen.getByTestId('stock-pie-chart');
      const graph = screen.getByTestId('portfolio-value-graph');
      const addButton = screen.getByTestId('add-stock-button');
      const stocksList = screen.getByTestId('stocks-list');

      // Verify all are rendered
      expect(pieChart).toBeTruthy();
      expect(graph).toBeTruthy();
      expect(addButton).toBeTruthy();
      expect(stocksList).toBeTruthy();

      // Verify DOM order using compareDocumentPosition
      // Node.DOCUMENT_POSITION_FOLLOWING = 4 means the argument node follows the reference node
      expect(pieChart.compareDocumentPosition(graph) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
      expect(graph.compareDocumentPosition(addButton) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
      expect(addButton.compareDocumentPosition(stocksList) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });

    it('passes summary and priceMap to StockPieChart and StocksList', async () => {
      const summaryData = [
        { symbol: 'AAPL', totalQuantity: 10, holdingCount: 1 },
        { symbol: 'TSLA', totalQuantity: 5, holdingCount: 2 },
      ];
      mockApiGet.mockResolvedValue({ data: summaryData, error: null, status: 200 });

      await act(async () => {
        render(<Dashboard />);
      });

      const pieChart = screen.getByTestId('stock-pie-chart');
      const stocksList = screen.getByTestId('stocks-list');

      expect(JSON.parse(pieChart.getAttribute('data-summary'))).toEqual(summaryData);
      expect(JSON.parse(stocksList.getAttribute('data-summary'))).toEqual(summaryData);
    });
  });

  describe('WebSocket connection (Req 10.1)', () => {
    it('establishes WebSocket connection on mount', async () => {
      mockApiGet.mockResolvedValue({ data: [], error: null, status: 200 });

      await act(async () => {
        render(<Dashboard />);
      });

      // The useWebSocket mock was called (connection established)
      // Verify the onMessage callback was captured
      expect(mockWsOnMessage).toBeDefined();
      expect(typeof mockWsOnMessage).toBe('function');
    });

    it('passes wsStatus to ConnectionIndicator', async () => {
      mockWsStatus = 'reconnecting';
      mockApiGet.mockResolvedValue({ data: [], error: null, status: 200 });

      await act(async () => {
        render(<Dashboard />);
      });

      const indicator = screen.getByTestId('connection-indicator');
      expect(indicator.getAttribute('data-status')).toBe('reconnecting');
    });

    it('calls disconnect on unmount', async () => {
      mockApiGet.mockResolvedValue({ data: [], error: null, status: 200 });

      let unmount;
      await act(async () => {
        const result = render(<Dashboard />);
        unmount = result.unmount;
      });

      act(() => {
        unmount();
      });

      expect(mockDisconnect).toHaveBeenCalled();
    });
  });

  describe('Price updates flow to child components (Req 3.2, 3.3)', () => {
    it('updates priceMap and passes it to children when price update received', async () => {
      const summaryData = [
        { symbol: 'AAPL', totalQuantity: 10, holdingCount: 1 },
      ];
      mockApiGet.mockResolvedValue({ data: summaryData, error: null, status: 200 });

      await act(async () => {
        render(<Dashboard />);
      });

      // Simulate a price update via the captured onMessage callback
      await act(async () => {
        mockWsOnMessage({ symbol: 'AAPL', price: 150.25, timestamp: '2024-01-15T14:30:00.123Z' });
      });

      const pieChart = screen.getByTestId('stock-pie-chart');
      const priceMap = JSON.parse(pieChart.getAttribute('data-pricemap'));
      expect(priceMap).toEqual({ AAPL: 150.25 });
    });

    it('appends data points to PortfolioValueGraph on price update', async () => {
      const summaryData = [
        { symbol: 'AAPL', totalQuantity: 10, holdingCount: 1 },
      ];
      mockApiGet.mockResolvedValue({ data: summaryData, error: null, status: 200 });

      await act(async () => {
        render(<Dashboard />);
      });

      // Send a price update
      await act(async () => {
        mockWsOnMessage({ symbol: 'AAPL', price: 150.00, timestamp: '2024-01-15T14:30:00.000Z' });
      });

      const graph = screen.getByTestId('portfolio-value-graph');
      const dataPoints = JSON.parse(graph.getAttribute('data-datapoints'));

      expect(dataPoints.length).toBe(1);
      expect(dataPoints[0].value).toBe(1500); // 10 shares × $150
    });

    it('computes currentTotal and passes to PortfolioValueGraph', async () => {
      const summaryData = [
        { symbol: 'AAPL', totalQuantity: 10, holdingCount: 1 },
        { symbol: 'TSLA', totalQuantity: 5, holdingCount: 1 },
      ];
      mockApiGet.mockResolvedValue({ data: summaryData, error: null, status: 200 });

      await act(async () => {
        render(<Dashboard />);
      });

      // Send price updates for both symbols
      await act(async () => {
        mockWsOnMessage({ symbol: 'AAPL', price: 100, timestamp: '2024-01-15T14:30:00.000Z' });
      });
      await act(async () => {
        mockWsOnMessage({ symbol: 'TSLA', price: 200, timestamp: '2024-01-15T14:30:01.000Z' });
      });

      const graph = screen.getByTestId('portfolio-value-graph');
      // currentTotal = 10*100 + 5*200 = 2000
      expect(Number(graph.getAttribute('data-currenttotal'))).toBe(2000);
    });

    it('ignores malformed price updates (missing symbol or price)', async () => {
      mockApiGet.mockResolvedValue({ data: [], error: null, status: 200 });

      await act(async () => {
        render(<Dashboard />);
      });

      // Send malformed messages
      await act(async () => {
        mockWsOnMessage(null);
        mockWsOnMessage({});
        mockWsOnMessage({ symbol: 'AAPL' }); // missing price
      });

      const graph = screen.getByTestId('portfolio-value-graph');
      const dataPoints = JSON.parse(graph.getAttribute('data-datapoints'));
      expect(dataPoints.length).toBe(0);
    });
  });
});
