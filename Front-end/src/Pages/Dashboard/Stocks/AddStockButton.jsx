import './AddStockButton.css';

export default function AddStockButton({ onClick }) {
  return (
    <button className="add-stock-button" onClick={onClick}>
      + Add Stock
    </button>
  );
}
