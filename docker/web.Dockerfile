# syntax=docker/dockerfile:1
# Grafioschtrader web image: Caddy serving the Angular frontend and reverse-proxying
# the API to the backend container. Automatic HTTPS via Let's Encrypt when a domain
# is configured (GT_SITE_ADDRESS).
# Build context is the repository root:
#   docker build -f docker/web.Dockerfile .

# Angular bundles are architecture-independent — build on the host platform only.
FROM --platform=$BUILDPLATFORM node:22-bookworm AS build
WORKDIR /app
# package-lock.json is intentionally not committed (see frontend/.gitignore),
# so install with `npm install` — matching the project's CI build. Copying only
# package.json first keeps the dependency layer cached across source changes.
COPY frontend/package.json ./
RUN npm install
COPY frontend/ .
# The production build needs ~4 GB heap
ENV NODE_OPTIONS=--max-old-space-size=4096
RUN npm run buildprod

FROM caddy:2-alpine
COPY docker/Caddyfile /etc/caddy/Caddyfile
# buildprod uses --base-href /grafioschtrader/, so the app must live under that path
COPY --from=build /app/dist/browser/ /srv/grafioschtrader/
