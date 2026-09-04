## 2026-09-04 - Dynamic List Accessibility
**Learning:** Dynamic lists rendered with v-for that contain interactive controls frequently lack proper labels. Specifically ensure that input fields and generic icon-only buttons (like Element UI's el-icon-delete) within these loops explicitly include context-specific, localized aria-label and title attributes using template literals (e.g. `删除规则 ${index + 1}`) for screen reader distinction.
**Action:** Add loop-indexed aria-label and title attributes to dynamic inputs and buttons in Vue v-for loops.
