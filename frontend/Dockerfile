# =============================================================================
# Dockerfile — Multi-stage build for the Fraud Detection Dashboard UI.
#
# STAGE 1 (builder):
#   Uses Node.js to install dependencies and build the production bundle.
#   Vite compiles TypeScript + JSX into optimized static files (HTML, CSS, JS).
#
# STAGE 2 (runtime):
#   Uses nginx:alpine to serve the static files. Nginx is configured to:
#   1. Serve the React SPA (index.html) for all routes
#   2. Proxy /api/* requests to the sse-stream backend container
#
# WHY NGINX INSTEAD OF SERVING FROM NODE?
#   The production build is just static files — there's no server-side logic.
#   Nginx is purpose-built for serving static assets with minimal memory
#   footprint (~5MB vs ~150MB for Node.js). It also handles gzip compression,
#   caching headers, and reverse proxying natively.
# =============================================================================

# --- Stage 1: Build the React application ---
FROM node:20-alpine AS builder

WORKDIR /app

# Copy package files first for Docker layer caching.
# If dependencies haven't changed, Docker reuses this layer
# and skips the expensive `npm install` step.
COPY package.json package-lock.json* ./
RUN npm ci

# Copy source code and build the production bundle
COPY . .
RUN npm run build

# --- Stage 2: Serve with nginx ---
FROM nginx:alpine

# Copy the built static files from the builder stage
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy our custom nginx config that handles SPA routing and API proxying
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Nginx listens on port 80 by default
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
