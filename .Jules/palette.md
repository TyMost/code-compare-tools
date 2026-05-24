## 2024-05-24 - Accessibility for Icon-only Elements in Dynamic Lists
**Learning:** In Vue dynamic lists using Element UI, icon-only action buttons (like delete buttons for dynamic filter rules) often lack accessibility context because developers rely on visual proximity to the row item. Screen reader users lose this context entirely.
**Action:** Always ensure icon-only buttons, especially in repeated dynamic lists, include `aria-label` and `title` attributes that provide clear context (e.g., '删除规则') to maintain accessibility and usability for screen readers and keyboard users.
