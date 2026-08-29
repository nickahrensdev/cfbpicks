import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// GitHub Pages serves a project site from a subpath
// (<user>.github.io/<repo>/) rather than the domain root, so every asset
// and route has to know about that prefix. Left empty for local dev and
// for a custom-domain deploy, where the app really does live at "/".
const base = process.env.VITE_BASE_PATH || '/';

export default defineConfig({
  base,
  plugins: [react()],
  server: {
    port: 5173,
    // Lets you open the dev site from a phone on the same wifi.
    host: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
