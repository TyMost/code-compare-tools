## 2026-06-28 - [Accessibility labels on dynamic loop elements]
**Learning:** It is crucial to ensure that dynamic lists rendered with `v-for` that contain interactive controls have proper labels for a11y. Input fields and generic icon-only buttons (like Element UI's `el-icon-delete`) within these loops explicitly need context-specific, localized `aria-label` and `title` attributes.
**Action:** When adding or auditing lists or inputs within lists, proactively identify and add localized screen-reader and tooltip context properties (e.g. `title` and `aria-label`).
