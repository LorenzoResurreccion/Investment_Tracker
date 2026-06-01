import { useState, useEffect, useReducer, useRef } from 'react';
import useApi from '../../../hooks/useApi.js';
import { displayPlatform, validateInvestmentForm } from '../utils.js';
import './StockDetailPanel.css';

function fetchReducer(state, action) {
  switch (action.type) {
    case 'fetch':
      return { ...state, loading: true, error: null };
    case 'success':
      return { ...state, loading: false, error: null, holdings: action.holdings };
    case 'error':
      return { ...state, loading: false, error: action.error };
    case 'update':
      return { ...state, holdings: action.holdings };
    default:
      return state;
  }
}

export default function StockDetailPanel({ symbol, onHoldingChanged }) {
  const api = useApi();
  const [state, dispatch] = useReducer(fetchReducer, {
    holdings: [],
    loading: true,
    error: null,
  });
  const [editing, setEditing] = useState(false);
  const [editForms, setEditForms] = useState({});
  const [editErrors, setEditErrors] = useState({});
  const [message, setMessage] = useState(null);
  const [deletingId, setDeletingId] = useState(null);
  const [addingNew, setAddingNew] = useState(false);
  const [addForm, setAddForm] = useState({ quantity: '', platform: '' });
  const [addErrors, setAddErrors] = useState([]);
  const [fetchTrigger, setFetchTrigger] = useState(0);
  const abortRef = useRef(false);

  useEffect(() => {
    let cancelled = false;
    abortRef.current = false;

    const timeoutId = setTimeout(() => {
      if (!cancelled) {
        abortRef.current = true;
        dispatch({ type: 'error', error: 'Request timed out. Please try again.' });
      }
    }, 10000);

    api.get(`/investments/symbol/${symbol}`).then((result) => {
      clearTimeout(timeoutId);
      if (cancelled || abortRef.current) return;
      if (result.error) {
        dispatch({ type: 'error', error: result.error });
      } else {
        dispatch({ type: 'success', holdings: result.data || [] });
      }
    });

    return () => {
      cancelled = true;
      clearTimeout(timeoutId);
    };
  }, [symbol, fetchTrigger]); // eslint-disable-line react-hooks/exhaustive-deps

  function retryFetch() {
    dispatch({ type: 'fetch' });
    setFetchTrigger((prev) => prev + 1);
  }

  function enterEditMode() {
    // Pre-fill edit forms for all holdings
    const forms = {};
    for (const h of state.holdings) {
      forms[h.id] = { quantity: String(h.quantity), platform: h.platform || '' };
    }
    setEditForms(forms);
    setEditErrors({});
    setEditing(true);
    setAddingNew(false);
    setMessage(null);
  }

  function handleFieldChange(holdingId, field, value) {
    setEditForms((prev) => ({
      ...prev,
      [holdingId]: { ...prev[holdingId], [field]: value },
    }));
  }

  function confirmDelete(holdingId) {
    setDeletingId(holdingId);
  }

  function cancelDelete() {
    setDeletingId(null);
  }

  function executeDelete(holdingId) {
    api.del(`/investments/${holdingId}`).then((result) => {
      if (result.error) {
        setMessage({ type: 'error', text: result.error });
      } else {
        dispatch({
          type: 'update',
          holdings: state.holdings.filter((h) => h.id !== holdingId),
        });
        setDeletingId(null);
        const newForms = { ...editForms };
        delete newForms[holdingId];
        setEditForms(newForms);
        setMessage({ type: 'success', text: 'Deleted.' });
        onHoldingChanged();
      }
    });
  }

  function startAddHolding() {
    setAddingNew(true);
    setAddForm({ quantity: '', platform: '' });
    setAddErrors([]);
  }

  function cancelAddHolding() {
    setAddingNew(false);
    setAddErrors([]);
  }

  function submitAddHolding() {
    const errors = validateInvestmentForm({
      symbol,
      quantity: addForm.quantity,
      platform: addForm.platform,
    });
    if (errors.length > 0) {
      setAddErrors(errors);
      return;
    }
    setAddErrors([]);

    api
      .post('/investments', {
        symbol,
        quantity: Number(addForm.quantity),
        platform: addForm.platform || null,
      })
      .then((result) => {
        if (result.error) {
          setMessage({ type: 'error', text: result.error });
        } else {
          dispatch({
            type: 'update',
            holdings: [...state.holdings, result.data],
          });
          // Add the new holding to edit forms
          setEditForms((prev) => ({
            ...prev,
            [result.data.id]: {
              quantity: String(result.data.quantity),
              platform: result.data.platform || '',
            },
          }));
          setAddingNew(false);
          setMessage({ type: 'success', text: 'Holding added.' });
          onHoldingChanged();
        }
      });
  }

  async function saveAllAndExit() {
    // Save any holdings whose values changed
    let hasError = false;
    const newErrors = {};

    for (const holding of state.holdings) {
      const form = editForms[holding.id];
      if (!form) continue;

      const qtyChanged = Number(form.quantity) !== holding.quantity;
      const platChanged = (form.platform || '') !== (holding.platform || '');

      if (!qtyChanged && !platChanged) continue;

      const errors = validateInvestmentForm({
        symbol,
        quantity: form.quantity,
        platform: form.platform,
      });
      if (errors.length > 0) {
        newErrors[holding.id] = errors;
        hasError = true;
        continue;
      }

      const result = await api.put(`/investments/${holding.id}`, {
        symbol,
        quantity: Number(form.quantity),
        platform: form.platform || null,
      });

      if (result.error) {
        newErrors[holding.id] = [result.error];
        hasError = true;
      } else {
        dispatch({
          type: 'update',
          holdings: state.holdings.map((h) =>
            h.id === holding.id
              ? { ...h, quantity: result.data.quantity, platform: result.data.platform }
              : h
          ),
        });
      }
    }

    setEditErrors(newErrors);

    if (!hasError) {
      setEditing(false);
      setEditForms({});
      setEditErrors({});
      setAddingNew(false);
      setMessage({ type: 'success', text: 'Saved.' });
      onHoldingChanged();
    } else {
      setMessage({ type: 'error', text: 'Some holdings have errors. Fix them and try again.' });
    }
  }

  const { holdings, loading, error } = state;

  if (loading) {
    return (
      <div className="stock-detail-panel">
        <div className="stock-detail-loading" aria-label="Loading holdings">
          Loading holdings…
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="stock-detail-panel">
        <div className="stock-detail-error">
          <p>{error}</p>
          <button className="stock-detail-retry-btn" onClick={retryFetch}>
            Retry
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="stock-detail-panel">
      {message && (
        <div className={`stock-detail-message stock-detail-message--${message.type}`}>
          {message.text}
        </div>
      )}

      {holdings.length === 0 && !addingNew && (
        <p className="stock-detail-empty">No holdings found for {symbol}.</p>
      )}

      {holdings.map((holding) => (
        <div key={holding.id} className="stock-detail-holding">
          {deletingId === holding.id ? (
            <div className="stock-detail-confirm">
              <p>
                Delete {displayPlatform(holding.platform)} — qty {holding.quantity}?
              </p>
              <div className="stock-detail-confirm-actions">
                <button
                  className="stock-detail-btn stock-detail-btn--danger"
                  onClick={() => executeDelete(holding.id)}
                >
                  Confirm
                </button>
                <button
                  className="stock-detail-btn stock-detail-btn--secondary"
                  onClick={cancelDelete}
                >
                  Cancel
                </button>
              </div>
            </div>
          ) : editing ? (
            <div className="stock-detail-edit-row">
              <div className="stock-detail-edit-fields">
                <input
                  type="number"
                  className="stock-detail-input"
                  value={editForms[holding.id]?.quantity ?? ''}
                  onChange={(e) => handleFieldChange(holding.id, 'quantity', e.target.value)}
                  step="any"
                  min="0.000001"
                  max="999999999.99"
                  placeholder="Quantity"
                />
                <input
                  type="text"
                  className="stock-detail-input"
                  value={editForms[holding.id]?.platform ?? ''}
                  onChange={(e) => handleFieldChange(holding.id, 'platform', e.target.value)}
                  maxLength={100}
                  placeholder="Platform"
                />
                <button
                  className="stock-detail-btn stock-detail-btn--danger stock-detail-btn--small"
                  onClick={() => confirmDelete(holding.id)}
                  title="Delete"
                >
                  ✕
                </button>
              </div>
              {editErrors[holding.id] && (
                <ul className="stock-detail-edit-errors">
                  {editErrors[holding.id].map((err) => (
                    <li key={err}>{err}</li>
                  ))}
                </ul>
              )}
            </div>
          ) : (
            <div className="stock-detail-holding-info">
              <div className="stock-detail-holding-details">
                <span className="stock-detail-platform">
                  {displayPlatform(holding.platform)}
                </span>
                <span className="stock-detail-quantity">
                  Qty: {holding.quantity}
                </span>
                <span className="stock-detail-date">
                  {new Date(holding.createdAt).toLocaleDateString()}
                </span>
              </div>
            </div>
          )}
        </div>
      ))}

      {/* Add new holding form */}
      {editing && addingNew && (
        <div className="stock-detail-add-form">
          <div className="stock-detail-edit-fields">
            <input
              type="number"
              className="stock-detail-input"
              value={addForm.quantity}
              onChange={(e) =>
                setAddForm((prev) => ({ ...prev, quantity: e.target.value }))
              }
              step="any"
              min="0.000001"
              max="999999999.99"
              placeholder="Quantity"
            />
            <input
              type="text"
              className="stock-detail-input"
              value={addForm.platform}
              onChange={(e) =>
                setAddForm((prev) => ({ ...prev, platform: e.target.value }))
              }
              maxLength={100}
              placeholder="Platform"
            />
          </div>
          {addErrors.length > 0 && (
            <ul className="stock-detail-edit-errors">
              {addErrors.map((err) => (
                <li key={err}>{err}</li>
              ))}
            </ul>
          )}
          <div className="stock-detail-edit-actions">
            <button
              className="stock-detail-btn stock-detail-btn--primary"
              onClick={submitAddHolding}
            >
              Add
            </button>
            <button
              className="stock-detail-btn stock-detail-btn--secondary"
              onClick={cancelAddHolding}
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Bottom action bar */}
      <div className="stock-detail-bottom-actions">
        {!editing ? (
          <button
            className="stock-detail-btn stock-detail-btn--add"
            onClick={enterEditMode}
          >
            Edit Holdings
          </button>
        ) : (
          <div className="stock-detail-edit-bar">
            {!addingNew && (
              <button
                className="stock-detail-btn stock-detail-btn--add"
                onClick={startAddHolding}
              >
                + Add Holding
              </button>
            )}
            <button
              className="stock-detail-btn stock-detail-btn--primary"
              onClick={saveAllAndExit}
            >
              Save
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
