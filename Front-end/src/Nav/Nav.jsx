import './Nav.css';

/**
 * Top navigation bar with app title and logout button.
 *
 * @param {{ onLogout: function }} props
 */
export default function Nav({ onLogout }) {
  return (
    <nav className="nav-bar" aria-label="Main navigation">
      <h1>Investments</h1>
      <button className="nav-logout-button" onClick={onLogout}>
        Sign Out
      </button>
    </nav>
  );
}
