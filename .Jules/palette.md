## 2024-05-18 - ARIA Labels in Dynamic Lists
**Learning:** Interactive controls (inputs, icon-buttons) inside dynamic `v-for` lists require unique, context-specific ARIA labels. Standard `aria-label`s on generic list items can be ambiguous for screen readers if not distinguished (e.g., using loop index).
**Action:** When adding `el-input` or generic icon-only buttons (like `el-icon-delete`) within `v-for` loops, ensure they explicitly include context-specific, localized `aria-label` and `title` attributes that use the loop index or item data to maintain uniqueness.
