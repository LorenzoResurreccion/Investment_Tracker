import './DashboardSkeleton.css';

export default function DashboardSkeleton() {
  return (
    <div className="dashboard-skeleton" aria-busy="true" aria-label="Loading dashboard">
      {/* Pie chart placeholder */}
      <div className="skeleton-section skeleton-pie-chart">
        <div className="skeleton-circle skeleton-pulse" />
      </div>

      {/* Portfolio value graph placeholder */}
      <div className="skeleton-section skeleton-graph">
        <div className="skeleton-rect skeleton-graph-area skeleton-pulse" />
      </div>

      {/* Add stock button placeholder */}
      <div className="skeleton-section skeleton-add-button">
        <div className="skeleton-rect skeleton-button-area skeleton-pulse" />
      </div>

      {/* Stocks list placeholder */}
      <div className="skeleton-section skeleton-stocks-list">
        <div className="skeleton-rect skeleton-stock-row skeleton-pulse" />
        <div className="skeleton-rect skeleton-stock-row skeleton-pulse" />
        <div className="skeleton-rect skeleton-stock-row skeleton-pulse" />
        <div className="skeleton-rect skeleton-stock-row skeleton-pulse" />
      </div>
    </div>
  );
}
