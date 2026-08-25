## 2024-05-19 - Missing ARIA Labels in v-for Loops (Element UI)
**Learning:** Generic icon-only buttons (like `el-icon-delete`) inside Vue `v-for` loops (e.g., custom exclude patterns list in `DiffMatrixFilters.vue`) often lack context-specific labels, making them invisible or identical to screen readers.
**Action:** When auditing `v-for` generated lists, always ensure interactive controls have dynamic `aria-label` and `title` attributes that use the loop index or item value (e.g., `:aria-label="\`删除规则 ${index + 1}\`"`) to provide proper accessibility.
