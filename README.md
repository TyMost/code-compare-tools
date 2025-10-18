# Reconstruction Integration Guide

This directory bundles the rebuilt backend and frontend. The current setup wires
both sides together so you can browse the sample migration data without the
legacy mock layer.

## Backend
- Runs from `reconstruction/backend` via `mvn spring-boot:run`.
- `migration.project.roots` now points to `../examples/projectA` and
  `../examples/projectB`, so the default scan reads the sample projects shipped
  in `reconstruction/examples`.
- On first startup the backend auto-scans these examples and writes diff
  snapshots so the dashboard has meaningful demo data.
- Git incremental scanning remains a stub; full scans supply the data required
  by the dashboard and block list APIs.

## Frontend
- Start with `npm run serve` inside `reconstruction/frontend`; it now talks to
  the backend at the default proxy (`/api/v1/**`).
- The Axios mock adapter is disabled unless `VUE_APP_USE_MOCK=true` is provided
  (for example: `VUE_APP_USE_MOCK=true npm run serve`).
- Existing requests under `src/api/migration.js` therefore hit the live backend
  by default.

## Re-enabling the Mock Layer
When you need to demo the UI without the backend, export the environment flag
before running the dev server:

```bash
VUE_APP_USE_MOCK=true npm run serve
```

This restores the behaviour of `src/api/mock/index.js` and keeps the backend
untouched.
