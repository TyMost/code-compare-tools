# Reconstruction Integration Guide

This directory bundles the rebuilt backend and frontend. The current setup wires both sides together so you can browse the sample migration data without the legacy mock layer.

## Backend
- Runs from `backend/` via `mvn spring-boot:run`.
- `migration.project.roots` defaults to `examples/projectA` and `examples/projectB`, so sample repositories are available immediately after startup.
- The first boot triggers a full scan so comparison data is available once the application starts.
- `GitComparisonController` exposes `/api/v1/migration/git-comparison` for dual project summaries.
- `GET /api/v1/migration/git-comparison/batch-export` renders an Excel workbook listing per-file similarities plus weighted repository scores. It uses `migration.project` sources/targets and the Git refs from `migration.scan` unless you override them with `configPath` query parameter pointing to a YAML file. The YAML now also supports a `pairs:` section so you can enumerate explicit source→target comparisons (each pair可提供 `code` 与 `path`，path 未注册时会被自动扫描)。
- `IncrementalDiffFacade` aggregates scan metadata that powers the git comparison responses.
- `migration.scan.diff-engine` is mirrored to `diff.engine-type`: set `git` to stream Git hunks (`gitSnapshots` + `gitDiff` with hunk ranges and raw lines) or keep `default` to fall back to difflib output.

## Frontend
- From `frontend/` run `npm install && npm run serve`; requests to `/api/v1/**` proxy to the backend.
- `src/views/Dashboard.vue` exposes `/dashboard` with scan statistics.
- `src/views/GitComparisonView.vue` exposes `/git-comparison` for comparing two project scans.
- The Git comparison view now includes a “导出批量相似度” button that hits the batch export endpoint, optionally using a YAML configuration path and a “重新扫描 Git 差异” toggle to refresh diffs before exporting.
- The Vuex module `gitComparison` manages comparison state via `src/api/gitComparison.js`.
- For offline demos you can start with `VUE_APP_USE_MOCK=true npm run serve` to enable Axios mocks.

## Re-enabling the Mock Layer
If you need to demo the UI without a backend, export:

```bash
VUE_APP_USE_MOCK=true npm run serve
```

This restores the local mock in `src/api/mock/index.js`.
