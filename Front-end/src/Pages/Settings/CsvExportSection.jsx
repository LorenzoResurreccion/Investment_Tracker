import './CsvExportSection.css';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

export default function CsvExportSection() {
  function handleExport() {
    const token = localStorage.getItem('access_token');
    window.open(`${API_BASE_URL}/investments/export?token=${token}`, '_blank');
  }

  return (
    <section className="csv-export-section">
      <h2 className="csv-export-section__title">Export</h2>

      <div className="csv-export-section__content">
        <span className="csv-export-section__label">Download your holdings as a CSV file</span>
        <button
          type="button"
          className="csv-export-section__btn"
          onClick={handleExport}
        >
          Export CSV
        </button>
      </div>
    </section>
  );
}
