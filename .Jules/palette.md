## 2024-06-25 - Icon-only buttons in dynamic lists

**Learning:** When auditing Vue components for accessibility, dynamic lists rendered with `v-for` that contain interactive controls frequently lack proper labels.
**Action:** Specifically ensure that input fields and generic icon-only buttons (like Element UI's `el-icon-delete`) within these loops explicitly include context-specific, localized `aria-label` and `title` attributes.
