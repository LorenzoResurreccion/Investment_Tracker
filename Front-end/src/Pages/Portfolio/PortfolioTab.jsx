import { useState } from 'react';
import StockPieChart from '../Dashboard/Charts/StockPieChart.jsx';
import HoldingsList from './HoldingsList.jsx';
import AddStockButton from '../Dashboard/Stocks/AddStockButton.jsx';
import AddStockForm from '../Dashboard/Stocks/AddStockForm.jsx';
import './PortfolioTab.css';

export default function PortfolioTab({ summary, priceMap, onHoldingChanged }) {
  const [addFormOpen, setAddFormOpen] = useState(false);

  function handleAddFormOpen() {
    setAddFormOpen(true);
  }

  function handleAddFormClose() {
    setAddFormOpen(false);
  }

  function handleStockCreated() {
    onHoldingChanged();
  }

  return (
    <div className="portfolio-tab">
      <section className="portfolio-tab__chart">
        <StockPieChart summary={summary} priceMap={priceMap} />
      </section>

      <section className="portfolio-tab__add-stock">
        <AddStockButton onClick={handleAddFormOpen} />
      </section>

      <section className="portfolio-tab__holdings">
        <HoldingsList
          summary={summary}
          priceMap={priceMap}
          onHoldingChanged={onHoldingChanged}
        />
      </section>

      <AddStockForm
        open={addFormOpen}
        onClose={handleAddFormClose}
        onCreated={handleStockCreated}
      />
    </div>
  );
}
