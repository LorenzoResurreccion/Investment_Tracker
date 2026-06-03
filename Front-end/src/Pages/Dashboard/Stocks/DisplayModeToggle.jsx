import './DisplayModeToggle.css';

export default function DisplayModeToggle({ mode, onChange }) {
  return (
    <div className="display-mode-toggle" role="radiogroup" aria-label="Display mode">
      <button
        role="radio"
        aria-checked={mode === 'totalValue'}
        className={`display-mode-toggle__btn ${mode === 'totalValue' ? 'display-mode-toggle__btn--active' : ''}`}
        onClick={() => onChange('totalValue')}
      >
        Total Value
      </button>
      <button
        role="radio"
        aria-checked={mode === 'profitLoss'}
        className={`display-mode-toggle__btn ${mode === 'profitLoss' ? 'display-mode-toggle__btn--active' : ''}`}
        onClick={() => onChange('profitLoss')}
      >
        Profit/Loss
      </button>
    </div>
  );
}
