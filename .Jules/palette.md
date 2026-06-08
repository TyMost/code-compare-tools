## 2024-06-08 - Accessible Icon Buttons in Dynamic Lists
**Learning:** Icon-only buttons in dynamic lists (like delete pattern buttons) often lack context for screen readers. Even though visually their purpose is implied by their position next to list items, they need explicit labels.
**Action:** Always add `aria-label` and `title` attributes (e.g. `aria-label="删除规则" title="删除规则"`) to icon-only action buttons in lists to ensure context is available both to screen readers and via mouse hover tooltips.
