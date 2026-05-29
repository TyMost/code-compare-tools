## 2024-05-24 - Accessibility for Dynamic List Action Buttons
**Learning:** Icon-only action buttons (like delete, edit) that are generated dynamically within lists or form arrays need explicit `aria-label`s and `title`s to ensure users and screen readers understand their function contextually.
**Action:** Always add `aria-label` and `title` properties (e.g., "删除规则") to icon-only `<el-button>`s used inside `v-for` loops or dynamic lists.
