## 2026-07-29 - [Dynamic List Accessibility]
**Learning:** Dynamic lists rendered with Vue v-for loops containing interactive elements (like inputs and icon-only delete buttons) often lack screen reader context, making it hard to distinguish between different items in the list.
**Action:** Use template literals in v-for loops to append unique loop indices to `aria-label` and `title` attributes (e.g., `:aria-label="\`删除规则 ${index + 1}\`"`) to ensure proper screen reader distinction.
