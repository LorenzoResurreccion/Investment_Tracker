/**
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
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

// --- Default props helper ---
function defaultProps(overrides = {}) {
  return {
    summary: [],
    priceMap: {},
    wsStatus: 'connected',
    onHoldingChanged: vi.fn(),
    ...overrides,
  };
}

// --- Test setup ---
afterEach(() => {
  vi.restoreAllMocks();
});

describe('Dashboard', () => {
  describe('Layout order (Req 1.1, 1.2, 1.3)', () => {
    it('renders components in correct order: PieChart → Graph → AddStockButton → StocksList', () => {
      const props = defaultProps({
        summary: [{ symbol: 'AAPL', totalQuantity: 10, holdingCount: 1 }],
      });

      render(<Dashboard {...props} />);

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
      expect(pieChart.compareDocumentPosition(graph) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
      expect(graph.compareDocumentPosition(addButton) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
      expect(addButton.compareDocumentPosition(stocksList) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });

    it('passes summary and priceMap to StockPieChart and StocksList', () => {
      const summaryData = [
        { symbol: 'AAPL', totalQuantity: 10, holdingCount: 1 },
        { symbol: 'TSLA', totalQuantity: 5, holdingCount: 2 },
      ];
      const props = defaultProps({ summary: summaryData });

      render(<Dashboard {...props} />);

      const pieChart = screen.getByTestId('stock-pie-chart');
      const stocksList = screen.getByTestId('stocks-list');

      expect(JSON.parse(pieChart.getAttribute('data-summary'))).toEqual(summaryData);
      expect(JSON.parse(stocksList.getAttribute('data-summary'))).toEqual(summaryData);
    });
  });

  describe('Connection indicator (Req 10.1)', () => {
    it('passes wsStatus to ConnectionIndicator', () => {
      const props = defaultProps({ wsStatus: 'reconnecting' });

      render(<Dashboard {...props} />);

      const indicator = screen.getByTestId('connection-indicator');
      expect(indicator.getAttribute('data-status')).toBe('reconnecting');
    });
  });

  describe('Price updates flow to child components (Req 3.2, 3.3)', () => {
    it('passes priceMap to children', () => {
      const summaryData = [
        { symbol: 'AAPL', totalQuantity: 10, holdingCount: 1 },
      ];
      const props = defaultProps({
        summary: summaryData,
        priceMap: { AAPL: 150.25 },
      });

      render(<Dashboard {...props} />);

      const stocksList = screen.getByTestId('stocks-list');
      const priceMap = JSON.parse(stocksList.getAttribute('data-pricemap'));
      expect(priceMap).toEqual({ AAPL: 150.25 });
    });

    it('appends data points to PortfolioValueGraph when priceMap changes', () => {
      const summaryData = [
        { symbol: 'AAPL', totalQuantity: 10, holdingCount: 1 },
      ];
      const props = defaultProps({
        summary: summaryData,
        priceMap: { AAPL: 150.00 },
      });

      render(<Dashboard {...props} />);

      const graph = screen.getByTestId('portfolio-value-graph');
      const dataPoints = JSON.parse(graph.getAttribute('data-datapoints'));

      // Should have a data point from the priceMap effect
      expect(dataPoints.length).toBe(1);
      expect(dataPoints[0].value).toBe(1500); // 10 shares × $150
    });

    it('computes currentTotal and passes to PortfolioValueGraph', () => {
      const summaryData = [
        { symbol: 'AAPL', totalQuantity: 10, holdingCount: 1 },
        { symbol: 'TSLA', totalQuantity: 5, holdingCount: 1 },
      ];
      const props = defaultProps({
        summary: summaryData,
        priceMap: { AAPL: 100, TSLA: 200 },
      });

      render(<Dashboard {...props} />);

      const graph = screen.getByTestId('portfolio-value-graph');
      // currentTotal = 10*100 + 5*200 = 2000
      expect(Number(graph.getAttribute('data-currenttotal'))).toBe(2000);
    });

    it('does not append data points when priceMap is empty', () => {
      const props = defaultProps({
        summary: [{ symbol: 'AAPL', totalQuantity: 10, holdingCount: 1 }],
        priceMap: {},
      });

      render(<Dashboard {...props} />);

      const graph = screen.getByTestId('portfolio-value-graph');
      const dataPoints = JSON.parse(graph.getAttribute('data-datapoints'));
      expect(dataPoints.length).toBe(0);
    });
  });
});
