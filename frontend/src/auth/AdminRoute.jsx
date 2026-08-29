import { Alert, Container } from 'react-bootstrap';

import { useProfile } from './ProfileProvider.jsx';
import { Loading } from '../components/common.jsx';

/**
 * Client-side gate. Convenience only - every admin endpoint checks the role
 * server-side, so hiding the page is presentation, not protection.
 */
export default function AdminRoute({ children }) {
  const { profile, loading, isAdmin } = useProfile();

  if (loading || !profile) {
    return <Loading label="Checking permissions" />;
  }

  if (!isAdmin) {
    return (
      <Container className="py-5">
        <Alert variant="warning">
          <Alert.Heading className="h5">Admins only</Alert.Heading>
          <p className="mb-0">
            Your account does not have the admin role. Ask an existing admin to grant it from the
            Admin → Members page.
          </p>
        </Alert>
      </Container>
    );
  }

  return children;
}
