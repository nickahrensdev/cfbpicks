import { Navigate, useLocation } from 'react-router-dom';
import { Spinner } from 'react-bootstrap';

import { useAuth } from './AuthProvider.jsx';

export default function ProtectedRoute({ children }) {
  const { session, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div className="text-center py-5">
        <Spinner animation="border" role="status" aria-label="Checking your session" />
      </div>
    );
  }

  if (!session) {
    // Remember where they were headed so login can send them back.
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return children;
}
