/**
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import AddStockForm from '../Stocks/AddStockForm.jsx';

// Mock global fetch
let fetchMock;

beforeEach(() => {
  fetchMock = vi.fn();
  global.fetch = fetchMock;
});

afterEach(() => {
  vi.restoreAllMocks();
});

/** Helper to get the submit button (avoids conflict with h3 title text) */
function getSubmitButton() {
  return screen.getByRole('button', { name: /add investment/i });
}

/**
 * Helper: creates a successful JSON response
 */
function jsonResponse(data, status = 200) {
  return Promise.resolve({
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(data),
  });
}

/**
 * Helper: creates an error JSON response
 */
function errorResponse(message, status = 400) {
  return Promise.resolve({
    ok: false,
    status,
    json: () => Promise.resolve({ message }),
  });
}

describe('AddStockForm', () => {
  describe('Form open/close behavior (Req 8.1, 8.6)', () => {
    it('renders nothing when open is false', () => {
      const { container } = render(
        <AddStockForm open={false} onClose={vi.fn()} onCreated={vi.fn()} />
      );
      expect(container.innerHTML).toBe('');
    });

    it('renders the form dialog when open is true', () => {
      render(
        <AddStockForm open={true} onClose={vi.fn()} onCreated={vi.fn()} />
      );
      expect(screen.getByRole('dialog', { name: /add new stock/i })).toBeTruthy();
      expect(screen.getByLabelText(/symbol/i)).toBeTruthy();
      expect(screen.getByLabelText(/quantity/i)).toBeTruthy();
      expect(screen.getByLabelText(/platform/i)).toBeTruthy();
    });

    it('calls onClose when cancel button is clicked', () => {
      const onClose = vi.fn();
      render(
        <AddStockForm open={true} onClose={onClose} onCreated={vi.fn()} />
      );
      fireEvent.click(screen.getByText('Cancel'));
      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('does not call onCreated when cancel is clicked', () => {
      const onCreated = vi.fn();
      render(
        <AddStockForm open={true} onClose={vi.fn()} onCreated={onCreated} />
      );
      fireEvent.click(screen.getByText('Cancel'));
      expect(onCreated).not.toHaveBeenCalled();
    });
  });

  describe('Symbol search debounce and display (Req 8.2)', () => {
    it('does not search immediately on typing (debounce)', async () => {
      vi.useFakeTimers();
      try {
        render(
          <AddStockForm open={true} onClose={vi.fn()} onCreated={vi.fn()} />
        );
        const symbolInput = screen.getByLabelText(/symbol/i);

        await act(async () => {
          fireEvent.change(symbolInput, { target: { value: 'AA' } });
        });

        // Advance only 100ms — less than 300ms debounce
        await act(async () => {
          vi.advanceTimersByTime(100);
        });

        expect(fetchMock).not.toHaveBeenCalled();
      } finally {
        vi.useRealTimers();
      }
    });

    it('searches after 300ms debounce', async () => {
      vi.useFakeTimers();
      try {
        fetchMock.mockReturnValue(
          jsonResponse([
            { symbol: 'AAPL', description: 'Apple Inc' },
            { symbol: 'AAL', description: 'American Airlines' },
          ])
        );

        render(
          <AddStockForm open={true} onClose={vi.fn()} onCreated={vi.fn()} />
        );
        const symbolInput = screen.getByLabelText(/symbol/i);

        await act(async () => {
          fireEvent.change(symbolInput, { target: { value: 'AA' } });
        });

        // Advance past debounce
        await act(async () => {
          vi.advanceTimersByTime(300);
        });

        expect(fetchMock).toHaveBeenCalledWith(
          expect.stringContaining('/api/symbols/search?q=AA'),
          expect.any(Object)
        );
      } finally {
        vi.useRealTimers();
      }
    });

    it('displays up to 10 search results', async () => {
      const results = Array.from({ length: 12 }, (_, i) => ({
        symbol: `SYM${i}`,
        description: `Company ${i}`,
      }));
      fetchMock.mockReturnValue(jsonResponse(results));

      vi.useFakeTimers();
      try {
        render(
          <AddStockForm open={true} onClose={vi.fn()} onCreated={vi.fn()} />
        );
        const symbolInput = screen.getByLabelText(/symbol/i);

        await act(async () => {
          fireEvent.change(symbolInput, { target: { value: 'SYM' } });
        });

        await act(async () => {
          vi.advanceTimersByTime(300);
        });

        // Allow microtasks to flush (fetch promise resolution)
        await act(async () => {
          await vi.advanceTimersByTimeAsync(0);
        });

        const listItems = screen.getAllByRole('listitem');
        expect(listItems.length).toBe(10);
      } finally {
        vi.useRealTimers();
      }
    });

    it('displays search error when API fails', async () => {
      fetchMock.mockReturnValue(errorResponse('Server error', 500));

      vi.useFakeTimers();
      try {
        render(
          <AddStockForm open={true} onClose={vi.fn()} onCreated={vi.fn()} />
        );
        const symbolInput = screen.getByLabelText(/symbol/i);

        await act(async () => {
          fireEvent.change(symbolInput, { target: { value: 'XYZ' } });
        });

        await act(async () => {
          vi.advanceTimersByTime(300);
        });

        await act(async () => {
          await vi.advanceTimersByTimeAsync(0);
        });

        expect(screen.getByText('Symbol search is unavailable')).toBeTruthy();
      } finally {
        vi.useRealTimers();
      }
    });
  });

  describe('Validation error display (Req 8.3)', () => {
    it('shows error when symbol is blank', async () => {
      render(
        <AddStockForm open={true} onClose={vi.fn()} onCreated={vi.fn()} />
      );

      await act(async () => {
        fireEvent.click(getSubmitButton());
      });

      expect(screen.getByText('Symbol is required')).toBeTruthy();
    });

    it('shows error when quantity is empty', async () => {
      render(
        <AddStockForm open={true} onClose={vi.fn()} onCreated={vi.fn()} />
      );
      fireEvent.change(screen.getByLabelText(/symbol/i), { target: { value: 'AAPL' } });

      await act(async () => {
        fireEvent.click(getSubmitButton());
      });

      expect(screen.getByText(/quantity must be between/i)).toBeTruthy();
    });

    it('shows error when quantity is below minimum', async () => {
      render(
        <AddStockForm open={true} onClose={vi.fn()} onCreated={vi.fn()} />
      );
      fireEvent.change(screen.getByLabelText(/symbol/i), { target: { value: 'AAPL' } });
      fireEvent.change(screen.getByLabelText(/quantity/i), { target: { value: '0' } });

      await act(async () => {
        fireEvent.click(getSubmitButton());
      });

      expect(screen.getByText(/quantity must be between/i)).toBeTruthy();
    });

    it('shows error when quantity exceeds maximum', async () => {
      render(
        <AddStockForm open={true} onClose={vi.fn()} onCreated={vi.fn()} />
      );
      fireEvent.change(screen.getByLabelText(/symbol/i), { target: { value: 'AAPL' } });
      fireEvent.change(screen.getByLabelText(/quantity/i), { target: { value: '9999999999' } });

      await act(async () => {
        fireEvent.click(getSubmitButton());
      });

      expect(screen.getByText(/quantity must be between/i)).toBeTruthy();
    });

    it('shows error when platform exceeds 100 characters', async () => {
      render(
        <AddStockForm open={true} onClose={vi.fn()} onCreated={vi.fn()} />
      );
      fireEvent.change(screen.getByLabelText(/symbol/i), { target: { value: 'AAPL' } });
      fireEvent.change(screen.getByLabelText(/quantity/i), { target: { value: '10' } });
      fireEvent.change(screen.getByLabelText(/platform/i), {
        target: { value: 'A'.repeat(101) },
      });

      await act(async () => {
        fireEvent.click(getSubmitButton());
      });

      expect(screen.getByText(/platform must not exceed 100 characters/i)).toBeTruthy();
    });

    it('does not submit when validation fails', async () => {
      render(
        <AddStockForm open={true} onClose={vi.fn()} onCreated={vi.fn()} />
      );

      await act(async () => {
        fireEvent.click(getSubmitButton());
      });

      // fetch should not have been called for POST
      expect(fetchMock).not.toHaveBeenCalled();
    });
  });

  describe('Successful submission flow (Req 8.3, 8.5)', () => {
    it('calls POST /api/investments on valid submit and triggers onCreated + onClose', async () => {
      const onCreated = vi.fn();
      const onClose = vi.fn();

      fetchMock.mockReturnValue(
        jsonResponse({ id: 1, symbol: 'AAPL', quantity: 10, platform: 'Robinhood' }, 201)
      );

      render(
        <AddStockForm open={true} onClose={onClose} onCreated={onCreated} />
      );

      fireEvent.change(screen.getByLabelText(/symbol/i), { target: { value: 'AAPL' } });
      fireEvent.change(screen.getByLabelText(/quantity/i), { target: { value: '10' } });
      fireEvent.change(screen.getByLabelText(/platform/i), { target: { value: 'Robinhood' } });

      await act(async () => {
        fireEvent.click(getSubmitButton());
      });

      // Verify POST was called with correct payload
      await waitFor(() => {
        expect(fetchMock).toHaveBeenCalledWith(
          '/api/investments',
          expect.objectContaining({
            method: 'POST',
            body: JSON.stringify({ symbol: 'AAPL', quantity: 10, platform: 'Robinhood' }),
          })
        );
      });

      await waitFor(() => {
        expect(onCreated).toHaveBeenCalledTimes(1);
        expect(onClose).toHaveBeenCalledTimes(1);
      });
    });

    it('sends null platform when platform field is empty', async () => {
      fetchMock.mockReturnValue(
        jsonResponse({ id: 2, symbol: 'TSLA', quantity: 5, platform: null }, 201)
      );

      render(
        <AddStockForm open={true} onClose={vi.fn()} onCreated={vi.fn()} />
      );

      fireEvent.change(screen.getByLabelText(/symbol/i), { target: { value: 'TSLA' } });
      fireEvent.change(screen.getByLabelText(/quantity/i), { target: { value: '5' } });

      await act(async () => {
        fireEvent.click(getSubmitButton());
      });

      await waitFor(() => {
        expect(fetchMock).toHaveBeenCalledWith(
          '/api/investments',
          expect.objectContaining({
            method: 'POST',
            body: JSON.stringify({ symbol: 'TSLA', quantity: 5, platform: null }),
          })
        );
      });
    });

    it('displays API validation error and preserves entered data (Req 8.5)', async () => {
      fetchMock.mockReturnValue(
        errorResponse('Symbol INVALID is not supported', 400)
      );

      render(
        <AddStockForm open={true} onClose={vi.fn()} onCreated={vi.fn()} />
      );

      fireEvent.change(screen.getByLabelText(/symbol/i), { target: { value: 'INVALID' } });
      fireEvent.change(screen.getByLabelText(/quantity/i), { target: { value: '10' } });
      fireEvent.change(screen.getByLabelText(/platform/i), { target: { value: 'Fidelity' } });

      await act(async () => {
        fireEvent.click(getSubmitButton());
      });

      await waitFor(() => {
        expect(screen.getByText('Symbol INVALID is not supported')).toBeTruthy();
      });

      // Verify form data is preserved
      expect(screen.getByLabelText(/symbol/i).value).toBe('INVALID');
      expect(screen.getByLabelText(/quantity/i).value).toBe('10');
      expect(screen.getByLabelText(/platform/i).value).toBe('Fidelity');
    });

    it('does not call onCreated or onClose on API error', async () => {
      const onCreated = vi.fn();
      const onClose = vi.fn();

      fetchMock.mockReturnValue(errorResponse('Server error', 500));

      render(
        <AddStockForm open={true} onClose={onClose} onCreated={onCreated} />
      );

      fireEvent.change(screen.getByLabelText(/symbol/i), { target: { value: 'AAPL' } });
      fireEvent.change(screen.getByLabelText(/quantity/i), { target: { value: '10' } });

      await act(async () => {
        fireEvent.click(getSubmitButton());
      });

      await waitFor(() => {
        expect(screen.getByText('Server error')).toBeTruthy();
      });

      expect(onCreated).not.toHaveBeenCalled();
      expect(onClose).not.toHaveBeenCalled();
    });
  });
});
