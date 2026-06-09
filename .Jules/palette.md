## 2024-06-09 - Ensure ARIA attributes on Icon-only Elements in Element-UI
**Learning:** Found multiple instances where Element UI buttons containing only icons lacked proper aria-labels and titles. This prevents screen readers from announcing the button's action.
**Action:** Always verify custom exclude rule controls or other icon-only buttons include `aria-label` and `title` to maintain accessibility compliance across dynamic components.
