## 2026-08-15 - Dynamic List Accessibility
**Learning:** In Vue `v-for` loops rendering interactive lists (like exclude pattern rules), standard interactive controls often duplicate aria labels. It is important to dynamically index or reference row-specific info to ensure uniquely identifiable tooltips and labels for screen readers.
**Action:** Always verify `v-for` generated elements and bind `:title` and `:aria-label` using loop metadata such as `${index + 1}` to disambiguate identical generic inputs or icon buttons.
