## 2024-07-23 - Accessibility ARIA labels for icon-only buttons
**Learning:** Dynamic lists rendered with `v-for` that contain interactive controls frequently lack proper labels. Specifically ensure that input fields and generic icon-only buttons (like Element UI's `el-icon-delete`) within these loops explicitly include context-specific, localized `aria-label` and `title` attributes. Adding these improves accessibility significantly.
**Action:** Add `aria-label` and `title` attributes to icon-only buttons like `el-icon-delete` and `el-icon-plus` in `DiffMatrixFilters.vue`.
