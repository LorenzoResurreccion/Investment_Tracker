import { useState, useEffect, useRef } from 'react';
import useApi from '../../../hooks/useApi.js';
import { validateInvestmentForm } from '../utils.js';
import './AddStockForm.css';

/**
 * Inner form component that resets state via key remounting.
 */
const INVESTMENT_TYPES = [
  { value: 'stock', label: 'US Stock' },
  { value: 'crypto', label: 'Crypto' },
];

function AddStockFormInner({ onClose, onCreated }) {
  const api = useApi();
  const [symbol, setSymbol] = useState('');
  const [quantity, setQuantity] = useState('');
  const [platform, setPlatform] = useState('');
  const [investmentType, setInvestmentType] = useState('stock');
  const [fieldErrors, setFieldErrors] = useState({});
  const [apiError, setApiError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  // Symbol search state
  const [searchResults, setSearchResults] = useState([]);
  const [searchError, setSearchError] = useState(null);
  const [searching, setSearching] = useState(false);
  const [showResults, setShowResults] = useState(false);
  const debounceRef = useRef(null);
  const searchAbortRef = useRef(null);

  // Debounced symbol search
  useEffect(() => {
    if (debounceRef.current) {
      clearTimeout(debounceRef.current);
    }

    if (!symbol || symbol.trim().length === 0) {
      return;
    }

    const query = symbol.trim();

    debounceRef.current = setTimeout(() => {
      // Cancel any in-flight search
      if (searchAbortRef.current) {
        searchAbortRef.current.abort = true;
      }

      const searchToken = { abort: false };
      searchAbortRef.current = searchToken;

      setSearching(true);
      setSearchError(null);

      const timeoutId = setTimeout(() => {
        if (!searchToken.abort) {
          searchToken.abort = true;
          setSearching(false);
          setSearchError('Symbol search is unavailable');
          setSearchResults([]);
          setShowResults(true);
        }
      }, 3000);

      api.get(`/symbols/search?q=${encodeURIComponent(query)}&type=${encodeURIComponent(investmentType)}`).then((result) => {
        clearTimeout(timeoutId);
        if (searchToken.abort) return;

        setSearching(false);

        if (result.error) {
          setSearchError('Symbol search is unavailable');
          setSearchResults([]);
        } else {
          const results = (result.data || []).slice(0, 10);
          setSearchResults(results);
          setSearchError(null);
        }
        setShowResults(true);
      });
    }, 300);

    return () => {
      if (debounceRef.current) {
        clearTimeout(debounceRef.current);
      }
    };
  }, [symbol, investmentType]); // eslint-disable-line react-hooks/exhaustive-deps

  function handleSymbolSelect(selectedSymbol) {
    setSymbol(selectedSymbol);
    setShowResults(false);
    setSearchResults([]);
  }

  function handleSymbolChange(e) {
    const value = e.target.value;
    setSymbol(value);
    if (!value || value.trim().length === 0) {
      setSearchResults([]);
      setSearchError(null);
      setShowResults(false);
    }
    if (fieldErrors.symbol) {
      setFieldErrors((prev) => ({ ...prev, symbol: null }));
    }
  }

  function handleTypeChange(type) {
    setInvestmentType(type);
    // Clear previous results when switching type
    setSearchResults([]);
    setSearchError(null);
    setShowResults(false);
  }

  function handleQuantityChange(e) {
    setQuantity(e.target.value);
    if (fieldErrors.quantity) {
      setFieldErrors((prev) => ({ ...prev, quantity: null }));
    }
  }

  function handlePlatformChange(e) {
    setPlatform(e.target.value);
    if (fieldErrors.platform) {
      setFieldErrors((prev) => ({ ...prev, platform: null }));
    }
  }

  function handleSubmit(e) {
    e.preventDefault();

    const errors = validateInvestmentForm({
      symbol,
      quantity,
      platform,
    });

    if (errors.length > 0) {
      const mapped = {};
      for (const err of errors) {
        if (err.toLowerCase().includes('symbol')) {
          mapped.symbol = err;
        } else if (err.toLowerCase().includes('quantity')) {
          mapped.quantity = err;
        } else if (err.toLowerCase().includes('platform')) {
          mapped.platform = err;
        }
      }
      setFieldErrors(mapped);
      return;
    }

    setFieldErrors({});
    setApiError(null);
    setSubmitting(true);

    api
      .post('/investments', {
        symbol: symbol.trim(),
        quantity: Number(quantity),
        platform: platform.trim() || null,
      })
      .then((result) => {
        setSubmitting(false);
        if (result.error) {
          setApiError(result.error);
        } else {
          onCreated();
          onClose();
        }
      });
  }

  function handleCancel() {
    onClose();
  }

  return (
    <div className="add-stock-form-overlay">
      <div className="add-stock-form" role="dialog" aria-label="Add new stock">
        <h3 className="add-stock-form__title">Add Investment</h3>

        <form onSubmit={handleSubmit} noValidate>
          {apiError && (
            <div className="add-stock-form__api-error">{apiError}</div>
          )}

          {/* Investment type selector */}
          <div className="add-stock-form__field">
            <label>Investment Type</label>
            <div className="add-stock-form__type-selector">
              {INVESTMENT_TYPES.map((t) => (
                <button
                  key={t.value}
                  type="button"
                  className={`add-stock-form__type-btn${investmentType === t.value ? ' add-stock-form__type-btn--active' : ''}`}
                  onClick={() => handleTypeChange(t.value)}
                >
                  {t.label}
                </button>
              ))}
            </div>
          </div>

          {/* Symbol field with search */}
          <div className="add-stock-form__field">
            <label htmlFor="add-stock-symbol">Symbol</label>
            <div className="add-stock-form__symbol-wrapper">
              <input
                id="add-stock-symbol"
                type="text"
                value={symbol}
                onChange={handleSymbolChange}
                onFocus={() => {
                  if (searchResults.length > 0 || searchError) {
                    setShowResults(true);
                  }
                }}
                onBlur={() => {
                  // Delay hiding to allow click on results
                  setTimeout(() => setShowResults(false), 200);
                }}
                placeholder="Search for a symbol…"
                maxLength={20}
                autoComplete="off"
                aria-describedby={fieldErrors.symbol ? 'add-stock-symbol-error' : undefined}
                aria-invalid={!!fieldErrors.symbol}
              />
              {searching && (
                <span className="add-stock-form__search-indicator">Searching…</span>
              )}
              {showResults && (searchResults.length > 0 || searchError) && (
                <div className="add-stock-form__search-results">
                  {searchError ? (
                    <div className="add-stock-form__search-error">{searchError}</div>
                  ) : (
                    <ul>
                      {searchResults.map((result) => (
                        <li
                          key={result.symbol}
                          onMouseDown={() => handleSymbolSelect(result.symbol)}
                        >
                          <span className="add-stock-form__result-symbol">
                            {result.symbol}
                          </span>
                          <span className="add-stock-form__result-desc">
                            {result.description}
                          </span>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              )}
            </div>
            {fieldErrors.symbol && (
              <span id="add-stock-symbol-error" className="add-stock-form__error">
                {fieldErrors.symbol}
              </span>
            )}
          </div>

          {/* Quantity field */}
          <div className="add-stock-form__field">
            <label htmlFor="add-stock-quantity">Quantity</label>
            <input
              id="add-stock-quantity"
              type="number"
              value={quantity}
              onChange={handleQuantityChange}
              step="any"
              min="0.000001"
              max="999999999.99"
              placeholder="e.g. 10.5"
              aria-describedby={fieldErrors.quantity ? 'add-stock-quantity-error' : undefined}
              aria-invalid={!!fieldErrors.quantity}
            />
            {fieldErrors.quantity && (
              <span id="add-stock-quantity-error" className="add-stock-form__error">
                {fieldErrors.quantity}
              </span>
            )}
          </div>

          {/* Platform field */}
          <div className="add-stock-form__field">
            <label htmlFor="add-stock-platform">Platform (optional)</label>
            <input
              id="add-stock-platform"
              type="text"
              value={platform}
              onChange={handlePlatformChange}
              maxLength={100}
              placeholder="e.g. Robinhood"
              aria-describedby={fieldErrors.platform ? 'add-stock-platform-error' : undefined}
              aria-invalid={!!fieldErrors.platform}
            />
            {fieldErrors.platform && (
              <span id="add-stock-platform-error" className="add-stock-form__error">
                {fieldErrors.platform}
              </span>
            )}
          </div>

          {/* Actions */}
          <div className="add-stock-form__actions">
            <button
              type="submit"
              className="add-stock-form__btn add-stock-form__btn--primary"
              disabled={submitting}
            >
              {submitting ? 'Adding…' : 'Add Investment'}
            </button>
            <button
              type="button"
              className="add-stock-form__btn add-stock-form__btn--secondary"
              onClick={handleCancel}
              disabled={submitting}
            >
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

/**
 * AddStockForm wrapper — conditionally renders the inner form.
 * Unmounting/remounting resets all state naturally.
 * Props: open (boolean), onClose (fn), onCreated (fn)
 */
export default function AddStockForm({ open, onClose, onCreated }) {
  if (!open) return null;

  return <AddStockFormInner onClose={onClose} onCreated={onCreated} />;
}
