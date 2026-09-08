## 2024-09-08 - Accessible Loop Labels
**Learning:** Dynamic lists rendered with `v-for` that contain interactive controls frequently lack proper labels. Specifically ensure that input fields and generic icon-only buttons (like Element UI's `el-icon-delete`) within these loops explicitly include context-specific, localized `aria-label` and `title` attributes.
**Action:** When auditing Vue components for accessibility, always verify that `v-for` generated elements provide unique context to screen readers, utilizing the loop `index` or data.
