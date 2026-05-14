## 2024-05-24 - Accessibility improvements for icon-only buttons and form inputs
**Learning:** Icon-only buttons and form inputs without labels are inaccessible to screen reader users and lack helpful tooltips for sighted users. Adding `aria-label` and `title` to icon-only buttons, and `aria-label` to form inputs improves accessibility and overall UX significantly.
**Action:** Always verify that icon-only buttons and form inputs have appropriate ARIA labels and titles. Add them proactively during component updates.
