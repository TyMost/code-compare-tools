## 2024-06-12 - Missing ARIA labels in dynamic lists
**Learning:** Dynamic lists rendered with `v-for` that contain interactive controls frequently lack proper labels. Specifically, input fields and generic icon-only buttons (like Element UI's `el-icon-delete`) within these loops often miss context-specific, localized `aria-label` and `title` attributes.
**Action:** When auditing Vue components for accessibility, always ensure that inputs and icon-only buttons inside `v-for` loops explicitly include context-specific, localized `aria-label` and `title` attributes.
