## 2024-06-21 - [DiffMatrixFilters] Added accessibility attributes to dynamic list controls
**Learning:** Dynamic lists rendered with `v-for` that contain interactive controls frequently lack proper labels. In this specific Vue 2 Element UI app, generic icon-only buttons (like `el-icon-delete`) and inputs within loops need context-specific `aria-label` and `title` attributes (e.g. '删除规则').
**Action:** Always verify that input fields and icon-only buttons within `v-for` loops explicitly include `aria-label` and `title` to maintain accessibility standards.
