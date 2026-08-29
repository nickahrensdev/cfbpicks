import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';

import App from './App.jsx';
import { AuthProvider } from './auth/AuthProvider.jsx';
import { ProfileProvider } from './auth/ProfileProvider.jsx';
import './styles/theme.scss';

// Same subpath Vite's `base` uses (see vite.config.js) - both have to agree
// or a client-side navigation ends up one level off from where assets load.
// Trailing slash trimmed: react-router wants "/cfbpicks", not "/cfbpicks/".
const basename = (import.meta.env.VITE_BASE_PATH || '/').replace(/\/$/, '');

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter basename={basename}>
      <AuthProvider>
        <ProfileProvider>
          <App />
        </ProfileProvider>
      </AuthProvider>
    </BrowserRouter>
  </React.StrictMode>,
);
