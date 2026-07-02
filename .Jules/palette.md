## 2026-07-02 - Frontend a11y Improvements
**Learning:** Dynamic lists rendered with `v-for` that contain interactive controls frequently lack proper labels in Vue applications.
**Action:** Ensure that generic icon-only buttons (like Element UI's `el-icon-delete`) within these loops explicitly include context-specific, localized `aria-label` and `title` attributes.
