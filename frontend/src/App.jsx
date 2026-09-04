import { Navigate, Route, Routes } from 'react-router-dom';

import SiteNav from './components/SiteNav.jsx';
import GroupBar from './components/GroupBar.jsx';
import { usePointerBlur } from './lib/pointerFocus.js';
import SiteFooter from './components/SiteFooter.jsx';
import ProtectedRoute from './auth/ProtectedRoute.jsx';
import AdminRoute from './auth/AdminRoute.jsx';
import LoginPage from './pages/LoginPage.jsx';
import JoinByLinkPage from './pages/JoinByLinkPage.jsx';
import ConfirmEmailPage from './pages/ConfirmEmailPage.jsx';
import GamesPage from './pages/GamesPage.jsx';
import GameDetailPage from './pages/GameDetailPage.jsx';
import MemberPicksPage from './pages/MemberPicksPage.jsx';
import LeaderboardPage from './pages/LeaderboardPage.jsx';
import GroupsPage from './pages/GroupsPage.jsx';
import FindGroupPage from './pages/FindGroupPage.jsx';
import GroupDetailPage from './pages/GroupDetailPage.jsx';
import ProfilePage from './pages/ProfilePage.jsx';
import TeamPage from './pages/TeamPage.jsx';
import AthletePage from './pages/AthletePage.jsx';
import CoachPage from './pages/CoachPage.jsx';
import AdminUsersPage from './pages/admin/AdminUsersPage.jsx';
import AdminGroupsPage from './pages/admin/AdminGroupsPage.jsx';
import AdminGroupCreatePage from './pages/admin/AdminGroupCreatePage.jsx';
import AdminGroupEditPage from './pages/admin/AdminGroupEditPage.jsx';
import AdminDataPage from './pages/admin/AdminDataPage.jsx';
import DataLogPage from './pages/admin/DataLogPage.jsx';
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
  // A clicked button should not go on looking selected until you click
  // elsewhere. Keyboard focus is untouched.
  usePointerBlur();

  return (
    <div className="app-shell">
      <SiteNav />
      {/* The league every board below is rendered in. Renders nothing when
          there is no group selected, so the login page is unaffected. */}
      <GroupBar />
      <main className="app-main">
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          {/* Unguarded on purpose: an invitation has to explain itself to
              somebody who has no account yet. The page sends them on to sign
              in, carrying the token so they come back here. */}
          <Route path="/join/:token" element={<JoinByLinkPage />} />

          {/* Unguarded, like the invite page: the whole point is that nobody
              is signed in yet. The email carries a token hash and this
              exchanges it for a session - see ConfirmEmailPage for why the
              link comes here rather than to Supabase's own verify endpoint. */}
          <Route path="/confirm" element={<ConfirmEmailPage />} />

          <Route path="/" element={guarded(<GamesPage />)} />
          <Route path="/games/:id" element={guarded(<GameDetailPage />)} />
          {/* My picks is now a filter on the games board. */}
          <Route path="/my-picks" element={<Navigate to="/?mine=1" replace />} />
          <Route path="/members/:userId" element={guarded(<MemberPicksPage />)} />
          <Route path="/leaderboard" element={guarded(<LeaderboardPage />)} />
          <Route path="/groups" element={guarded(<GroupsPage />)} />
          {/* Before /groups/:id, which would otherwise match "find" and try
              to load a group whose id is the literal string. */}
          <Route path="/groups/find" element={guarded(<FindGroupPage />)} />
          <Route path="/groups/:id" element={guarded(<GroupDetailPage />)} />
          <Route path="/profile" element={guarded(<ProfilePage />)} />
          <Route path="/teams/:id" element={guarded(<TeamPage />)} />
          <Route path="/athletes/:id" element={guarded(<AthletePage />)} />
          <Route path="/coaches/:id" element={guarded(<CoachPage />)} />

          <Route path="/admin/members" element={adminOnly(<AdminUsersPage />)} />
          <Route path="/admin/groups" element={adminOnly(<AdminGroupsPage />)} />
          {/* Before /admin/groups/:id, which would otherwise match "new". */}
          <Route path="/admin/groups/new" element={adminOnly(<AdminGroupCreatePage />)} />
          <Route path="/admin/groups/:id" element={adminOnly(<AdminGroupEditPage />)} />
          <Route path="/admin/data" element={adminOnly(<AdminDataPage />)} />
          <Route path="/admin/data-log" element={adminOnly(<DataLogPage />)} />
          <Route path="/admin/activity" element={adminOnly(<ActivityLogPage />)} />

          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </main>
      <SiteFooter />
    </div>
  );
}
