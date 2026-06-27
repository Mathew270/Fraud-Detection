// =============================================================================
// vite.config.ts — Vite build configuration for the Fraud Detection Dashboard.
//
// KEY CONFIGURATION:
//
// 1. PROXY (/api → api-gateway:8090):
//    When running locally with `npm run dev`, the React app is served on
//    port 5173 but needs to call SSE and cluster management endpoints on port 8090.
//    Without a proxy, the browser would block these requests due to CORS (different
//    ports = different origins).
//
//    The proxy intercepts any request starting with /api/ and forwards it
//    to http://localhost:8090. The browser thinks it's talking to localhost:5173,
//    so no CORS issues arise.
//
//    In production (Docker), nginx handles this proxying instead.
//
// 2. PORT (5173):
//    Vite's default dev port. We keep it as-is since it doesn't conflict
//    with any of our Docker services (Kafka UI on 8080, Grafana on 3000, etc.).
// =============================================================================

import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],

  server: {
    // Dev server port — access the dashboard at http://localhost:5173
    port: 5173,

    // Proxy configuration for development
    // This avoids CORS errors when the React dev server (port 5173)
    // calls the API Gateway backend (port 8090).
    proxy: {
      "/api": {
        // Forward all /api/* requests to the API Gateway
        target: "http://localhost:8090",
        // changeOrigin rewrites the Host header to match the target,
        // which is necessary when the backend checks the Origin header.
        changeOrigin: true,
      },
    },
  },
});
