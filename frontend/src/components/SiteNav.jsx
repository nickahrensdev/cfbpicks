import { useEffect, useState } from 'react';
import { Badge, Button, Container, Nav, NavDropdown, Navbar } from 'react-bootstrap';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';

import { useAuth } from '../auth/AuthProvider.jsx';
import { useProfile } from '../auth/ProfileProvider.jsx';

// My picks is a filter on the games board, not a nav destination - the
// toggle lives with the other filters. /my-picks still redirects there for
// anyone with the old URL bookmarked.
const LINKS = [
  { to: '/', label: 'Games', end: true },
  { to: '/leaderboard', label: 'Leaderboard' },
];

const ADMIN_LINKS = [
  { to: '/admin/members', label: 'Members' },
  { to: '/admin/data', label: 'Data' },
  { to: '/admin/activity', label: 'Activity log' },
];

export default function SiteNav() {
  const [expanded, setExpanded] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();
  const { session, signOut } = useAuth();
  const { profile, isAdmin } = useProfile();

  // Collapse the burger menu after navigating on a phone.
  useEffect(() => setExpanded(false), [location.pathname]);

  const handleSignOut = async () => {
    await signOut();
    navigate('/login');
  };

  return (
    <Navbar
      expand="lg"
      bg="dark"
      variant="dark"
      sticky="top"
      expanded={expanded}
      onToggle={setExpanded}
      className="shadow-sm"
    >
      <Container>
        <Navbar.Brand as={Link} to="/" className="fw-bold">
          Nick&apos;s <span className="text-primary">Picks</span>
        </Navbar.Brand>
        <Navbar.Toggle aria-controls="main-nav" />
        <Navbar.Collapse id="main-nav">
          {session && (
            <Nav className="me-auto">
              {LINKS.map(({ to, label, end }) => (
                <Nav.Link key={to} as={NavLink} to={to} end={end}>
                  {label}
                </Nav.Link>
              ))}
              {isAdmin && (
                <NavDropdown title="Admin" id="admin-menu">
                  {ADMIN_LINKS.map(({ to, label }) => (
                    <NavDropdown.Item key={to} as={NavLink} to={to}>
                      {label}
                    </NavDropdown.Item>
                  ))}
                </NavDropdown>
              )}
            </Nav>
          )}

          <Nav className="ms-auto align-items-lg-center">
            {session ? (
              <>
                <Nav.Link as={NavLink} to="/profile" className="d-flex align-items-center gap-2">
                  {profile?.displayName ?? 'Profile'}
                  {isAdmin && (
                    <Badge bg="primary" className="fw-normal">
                      admin
                    </Badge>
                  )}
                </Nav.Link>
                <Button
                  variant="outline-light"
                  size="sm"
                  onClick={handleSignOut}
                  className="mt-2 mt-lg-0"
                >
                  Sign out
                </Button>
              </>
            ) : (
              <Nav.Link as={NavLink} to="/login">
                Sign in
              </Nav.Link>
            )}
          </Nav>
        </Navbar.Collapse>
      </Container>
    </Navbar>
  );
}
