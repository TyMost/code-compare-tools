## 2026-06-20 - Ensure icon buttons have labels
**Learning:** Found an `el-icon-delete` button inside `v-for` that lacked `aria-label` or `title`. Dynamic lists require dynamic ARIA labels.
**Action:** Always add bound ARIA labels to dynamically rendered Element UI icon buttons.
