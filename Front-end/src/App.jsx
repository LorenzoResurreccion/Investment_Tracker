import './App.css';
import useAuth from './hooks/useAuth.js';
import Dashboard from './Pages/Dashboard/Dashboard.jsx';
import Login from './Pages/Login/Login.jsx';
import AuthCallback from './Pages/AuthCallback/AuthCallback.jsx';
import Nav from './Nav/Nav.jsx';

function App() {
  const { login, logout, handleCallback, isAuthenticated } = useAuth();

  // Handle the /auth/callback route for Cognito redirect
  if (window.location.pathname === '/auth/callback') {
    return <AuthCallback onCallback={handleCallback} />;
  }

  // Show login page when not authenticated
  if (!isAuthenticated) {
    return <Login onLogin={login} />;
  }

  // Authenticated — render the main app
  return (
    <div>
      <Nav onLogout={logout} />
      <Dashboard />
    </div>
  );
}

export default App;
