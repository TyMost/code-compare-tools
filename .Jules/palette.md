## 2025-07-03 - Added ARIA label to Icon-Only Buttons
**Learning:** When auditing Vue components for accessibility, dynamic lists rendered with `v-for` that contain interactive controls frequently lack proper labels. Specifically ensure that generic icon-only buttons (like Element UI's `el-icon-delete`) within these loops explicitly include context-specific, localized `aria-label` and `title` attributes.
**Action:** Added `aria-label` and `title` attributes to icon-only `<el-button>` within `v-for` in `DiffMatrixFilters.vue`.
