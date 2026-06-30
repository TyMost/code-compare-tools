## 2026-06-30 - Add aria-labels to dynamic v-for loop elements
**Learning:** Dynamic lists rendered with `v-for` that contain interactive controls frequently lack proper labels, specifically input fields and generic icon-only buttons (like Element UI's `el-icon-delete`).
**Action:** Audit Vue components for dynamic lists and ensure elements within these loops explicitly include context-specific, localized `aria-label` and `title` attributes.
