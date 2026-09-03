import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';

import App from './App.jsx';
import { AuthProvider } from './auth/AuthProvider.jsx';
import { ProfileProvider } from './auth/ProfileProvider.jsx';
import { GroupProvider } from './auth/GroupProvider.jsx';
import { applyTheme, cachedTheme } from './lib/theme.js';
import { BASENAME } from './lib/appUrl.js';
import './styles/theme.scss';
import './styles/themes.css';

// Same subpath Vite's `base` uses (see vite.config.js) - both have to agree
// or a client-side navigation ends up one level off from where assets load.
// Trailing slash trimmed: react-router wants "/cfbpicks", not "/cfbpicks/".
// One definition, shared with appUrl() - a share link built against a
// different idea of the base path than the router's is a link to nowhere.
const basename = BASENAME;

// Applied before the first render, from whatever was last known, so there is
// no flash of the default theme while /api/me is still in flight.
// ProfileProvider re-applies once the authoritative value comes back.
applyTheme(...cachedTheme());

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter basename={basename}>
      <AuthProvider>
        <ProfileProvider>
          <GroupProvider>
            <App />
          </GroupProvider>
        </ProfileProvider>
      </AuthProvider>
    </BrowserRouter>
  </React.StrictMode>,
);
