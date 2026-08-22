# syntax=docker/dockerfile:1
# Grafioschtrader web image: Caddy serving the Angular frontend and reverse-proxying
# the API to the backend container. Automatic HTTPS via Let's Encrypt when a domain
# is configured (GT_SITE_ADDRESS).
# Build context is the repository root:
#   docker build -f docker/web.Dockerfile .

# Angular bundles are architecture-independent — build on the host platform only.
FROM --platform=$BUILDPLATFORM node:22.22-bookworm AS build
WORKDIR /app
# Angular 22 requires Node ^22.22.3, ^24.15.0 or >=26.0.0, hence the pinned 22.22 tag.
# frontend/package-lock.json is committed, so the dependencies are installed with
# `npm ci` - the same reproducible install the CI build uses. Copying only the two
# manifests first keeps the dependency layer cached across source changes.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ .
# The production build needs ~4 GB heap
ENV NODE_OPTIONS=--max-old-space-size=4096
RUN npm run buildprod

FROM caddy:2-alpine
COPY docker/Caddyfile /etc/caddy/Caddyfile
# buildprod uses --base-href /grafioschtrader/, so the app must live under that path
COPY --from=build /app/dist/browser/ /srv/grafioschtrader/
