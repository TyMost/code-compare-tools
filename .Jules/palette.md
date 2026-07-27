## 2024-07-27 - Added ARIA labels to dynamic exclude rule fields
**Learning:** In Vue components, generic inputs and icon-only delete buttons within dynamic `v-for` loops (such as exclude pattern lists) lack screen reader context. They need unique explicit aria-labels and titles using loop index variables, combined with localized Chinese copy, for proper accessibility.
**Action:** When adding inputs or action buttons inside `v-for` loops, always bind unique `:aria-label` and `:title` properties using template literals (e.g. `:aria-label="\`排除规则 ${index + 1}\`"`).
