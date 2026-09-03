## 2025-10-27 - Dynamic Labels in Lists

**Learning:** When generating interactive controls (such as `el-input` or icon-only `el-button`) dynamically in a `v-for` loop, it is crucial to use template literals involving the loop `index` or a unique item identifier to generate unique `aria-label` and `title` attributes. Without this, screen readers cannot distinguish between identically labeled controls in the list, making it difficult for visually impaired users to interact confidently. Also, `title` successfully propagates to native inputs in Element UI, providing an accessibility fallback.

**Action:** Always audit `v-for` loops in Vue components. If a loop renders interactive controls, ensure their `aria-label` and `title` properties are dynamic and unique (e.g., `:aria-label="\`删除规则 ${index + 1}\`"`).
