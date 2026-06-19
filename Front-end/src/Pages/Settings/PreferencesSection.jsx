import { useState } from 'react';
import { PREFERENCE_KEY, getStoredDisplayMode } from '../Analytics/preferences.js';
import './PreferencesSection.css';

export default function PreferencesSection() {
  const [displayMode, setDisplayMode] = useState(getStoredDisplayMode);

  function handleModeChange(mode) {
    setDisplayMode(mode);
    localStorage.setItem(PREFERENCE_KEY, mode);
  }

  return (
    <section className="preferences-section">
      <h2 className="preferences-section__title">Preferences</h2>

      <div className="preferences-section__option">
        <span className="preferences-section__label">Default View Mode</span>
        <div className="preferences-section__toggle">
          <button
            type="button"
            className={`preferences-section__toggle-btn${displayMode === 'totalValue' ? ' preferences-section__toggle-btn--active' : ''}`}
            onClick={() => handleModeChange('totalValue')}
            aria-pressed={displayMode === 'totalValue'}
          >
            Total Value
          </button>
          <button
            type="button"
            className={`preferences-section__toggle-btn${displayMode === 'profitLoss' ? ' preferences-section__toggle-btn--active' : ''}`}
            onClick={() => handleModeChange('profitLoss')}
            aria-pressed={displayMode === 'profitLoss'}
          >
            Profit/Loss
          </button>
        </div>
      </div>
    </section>
  );
}
