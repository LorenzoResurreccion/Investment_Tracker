import PreferencesSection from './PreferencesSection.jsx';
import CsvExportSection from './CsvExportSection.jsx';
import AccountDeletionSection from './AccountDeletionSection.jsx';
import './SettingsTab.css';

export default function SettingsTab({ summary, priceMap, onLogout }) {
  return (
    <div className="settings-tab">
      <h1 className="settings-tab__heading">Settings</h1>

      <PreferencesSection />

      <CsvExportSection />

      <AccountDeletionSection onLogout={onLogout} />
    </div>
  );
}
