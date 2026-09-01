## 2024-05-15 - Unique Labels in v-for loops for Screen Readers
**Learning:** Generic inputs and icon-only buttons (like `el-icon-delete`) inside Vue `v-for` loops must have unique `aria-label` and `title` attributes (e.g., using loop index or content) so screen readers can distinguish between them.
**Action:** Always append template literals with `index` or descriptive content (e.g., `:aria-label="\`删除规则 ${index + 1}\`"`) when defining ARIA attributes for repeated elements within dynamic lists.
