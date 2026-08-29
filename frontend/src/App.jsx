import { Navigate, Route, Routes } from 'react-router-dom';

import SiteNav from './components/SiteNav.jsx';
import SiteFooter from './components/SiteFooter.jsx';
import ProtectedRoute from './auth/ProtectedRoute.jsx';
import AdminRoute from './auth/AdminRoute.jsx';
import LoginPage from './pages/LoginPage.jsx';
import GamesPage from './pages/GamesPage.jsx';
import GameDetailPage from './pages/GameDetailPage.jsx';
import MemberPicksPage from './pages/MemberPicksPage.jsx';
import LeaderboardPage from './pages/LeaderboardPage.jsx';
import ProfilePage from './pages/ProfilePage.jsx';
import TeamPage from './pages/TeamPage.jsx';
import AthletePage from './pages/AthletePage.jsx';
import CoachPage from './pages/CoachPage.jsx';
import AdminUsersPage from './pages/admin/AdminUsersPage.jsx';
import AdminDataPage from './pages/admin/AdminDataPage.jsx';
import ActivityLogPage from './pages/admin/ActivityLogPage.jsx';
import NotFoundPage from './pages/NotFoundPage.jsx';

/** Everything except /login requires a session. */
const guarded = (element) => <ProtectedRoute>{element}</ProtectedRoute>;

/** Admin pages additionally require the role. Server-enforced too. */
const adminOnly = (element) => (
  <ProtectedRoute>
    <AdminRoute>{element}</AdminRoute>
  </ProtectedRoute>
);

export default function App() {
  return (
    <div className="app-shell">
      <SiteNav />
      <main className="app-main">
        <Routes>
          <Route path="/login" element={<LoginPage />} />

          <Route path="/" element={guarded(<GamesPage />)} />
          <Route path="/games/:id" element={guarded(<GameDetailPage />)} />
          {/* My picks is now a filter on the games board. */}
          <Route path="/my-picks" element={<Navigate to="/?mine=1" replace />} />
          <Route path="/members/:userId" element={guarded(<MemberPicksPage />)} />
          <Route path="/leaderboard" element={guarded(<LeaderboardPage />)} />
          <Route path="/profile" element={guarded(<ProfilePage />)} />
          <Route path="/teams/:id" element={guarded(<TeamPage />)} />
          <Route path="/athletes/:id" element={guarded(<AthletePage />)} />
          <Route path="/coaches/:id" element={guarded(<CoachPage />)} />

          <Route path="/admin/members" element={adminOnly(<AdminUsersPage />)} />
          <Route path="/admin/data" element={adminOnly(<AdminDataPage />)} />
          <Route path="/admin/activity" element={adminOnly(<ActivityLogPage />)} />

          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </main>
      <SiteFooter />
    </div>
  );
}
