## 2024-06-17 - Add ARIA labels in dynamic lists
**Learning:** Dynamic lists rendered with `v-for` that contain interactive controls frequently lack proper labels. In this project, `el-input` fields and generic icon-only buttons (like Element UI's `el-icon-delete`) within these loops must explicitly include context-specific, localized `aria-label` and `title` attributes.
**Action:** Always verify that interactive elements within `v-for` loops have appropriate `aria-label` and `title` attributes, written in the appropriate language (e.g., Chinese for this UI).
