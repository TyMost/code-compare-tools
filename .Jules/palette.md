## 2024-05-18 - Missing ARIA Labels in Dynamic Lists
**Learning:** When auditing Vue components for accessibility, dynamic lists rendered with `v-for` that contain interactive controls frequently lack proper labels. Specifically ensure that input fields and generic icon-only buttons (like Element UI's `el-icon-delete`) within these loops explicitly include context-specific, localized `aria-label` and `title` attributes.
**Action:** Audit and add `aria-label` and `title` attributes (e.g., '删除规则' for delete actions) to dynamic list items in components like `DiffMatrixFilters.vue`.
