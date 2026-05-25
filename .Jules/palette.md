## 2024-05-25 - Element UI Icon-only Button Accessibility
**Learning:** Found an accessibility issue pattern specific to this app's components: Element UI icon-only buttons (like the exclude pattern delete button in `DiffMatrixFilters.vue`) often lack screen reader support and tooltip context for sighted users.
**Action:** Always verify icon-only `<el-button>` components have `aria-label` and `title` attributes (using appropriate localized copy, e.g., '删除规则' for delete actions) added to improve UX and accessibility.
