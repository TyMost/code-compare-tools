## 2024-05-24 - Missing ARIA label on delete exclude pattern button
**Learning:** Icon-only buttons without `aria-label` or `title` attributes are a common accessibility issue in Element UI forms/components, specifically in dynamic lists where delete actions are represented by icons like `el-icon-delete`.
**Action:** Always verify that interactive icon elements inside loops (`v-for`) or dynamic forms have explicitly defined `aria-label` and `title` attributes with appropriate localization (e.g., "删除规则") to ensure screen readers can announce the action and users can see tooltips.
