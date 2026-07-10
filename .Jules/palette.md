## 2026-07-10 - Add ARIA labels to DiffMatrixFilters
**Learning:** Found dynamic lists generated via `v-for` missing ARIA labels on input fields and icon-only delete buttons. This makes it impossible for screen readers to identify the purpose of the input and the action of the button.
**Action:** Always ensure that dynamically generated inputs and icon-only buttons within loops have appropriate, localized `aria-label` and `title` attributes.
