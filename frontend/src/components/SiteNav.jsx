import { useEffect, useState } from 'react';
import { Badge, Button, Container, Nav, NavDropdown, Navbar } from 'react-bootstrap';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';

import { useAuth } from '../auth/AuthProvider.jsx';
import { useProfile } from '../auth/ProfileProvider.jsx';
import { handle } from './common.jsx';

// My picks is a filter on the games board, not a nav destination - the
// toggle lives with the other filters. /my-picks still redirects there for
// anyone with the old URL bookmarked.
const LINKS = [
  { to: '/', label: 'Games', end: true },
  { to: '/leaderboard', label: 'Leaderboard' },
  { to: '/groups', label: 'Groups' },
];

const ADMIN_LINKS = [
  { to: '/admin/members', label: 'Members' },
  { to: '/admin/groups', label: 'Groups' },
  { to: '/admin/data', label: 'Data' },
  { to: '/admin/data-log', label: 'Data log' },
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
      data-sticky="nav"
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

              {/* Desktop only. A dropdown inside an expanded burger menu is a
                  menu inside a menu - it opens downward over the links below
                  it and has to be dismissed before anything else can be
                  read. On a phone the same links are listed flat instead. */}
              {isAdmin && (
                <NavDropdown title="Admin" id="admin-menu" className="d-none d-lg-block">
                  {ADMIN_LINKS.map(({ to, label }) => (
                    <NavDropdown.Item key={to} as={NavLink} to={to}>
                      {label}
                    </NavDropdown.Item>
                  ))}
                </NavDropdown>
              )}
            </Nav>
          )}

          {/* The flat admin list the dropdown above stands in for on a phone,
              under a heading so it is clear these are a different class of
              destination rather than five more ordinary links. */}
          {session && isAdmin && (
            <div className="d-lg-none">
              <div className="site-nav-heading">Admin</div>
              <Nav>
                {ADMIN_LINKS.map(({ to, label }) => (
                  <Nav.Link key={to} as={NavLink} to={to}>
                    {label}
                  </Nav.Link>
                ))}
              </Nav>
            </div>
          )}

          {session && <div className="site-nav-heading d-lg-none">Account</div>}

          <Nav className="ms-auto align-items-lg-center">
            {session ? (
              <>
                <Nav.Link as={NavLink} to="/profile" className="d-flex align-items-center gap-2">
                  {/* The handle where it fits; on a phone the row is
                      already tight, and "My Profile" says what the link does
                      better than a name does. */}
                  <span className="d-none d-lg-inline">
                    {profile ? handle(profile.username) : 'Profile'}
                  </span>
                  <span className="d-lg-none">My Profile</span>
                  {isAdmin && (
                    <Badge bg="primary" className="fw-normal">
                      admin
                    </Badge>
                  )}
                </Nav.Link>
                {/* Full width on a phone, where it is the last thing in a
                    stacked menu and a button hugging its own text reads as
                    unfinished; inline and small once the bar is horizontal. */}
                <Button
                  variant="outline-light"
                  size="sm"
                  onClick={handleSignOut}
                  className="mt-2 mt-lg-0 w-100 w-lg-auto"
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
