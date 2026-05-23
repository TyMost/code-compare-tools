
## 2026-05-23 - Accessibility of icon-only action buttons
**Learning:** Element UI `el-button` components containing only an `el-icon` (e.g., `<i class="el-icon-delete"></i>`) fail accessibility guidelines if they lack semantic labeling.
**Action:** Always ensure that icon-only buttons include `aria-label` and `title` attributes that describe the button's action (e.g., `aria-label="删除规则" title="删除规则"`), particularly within complex components like filters. This helps screen readers understand the button's intent and provides tooltips for mouse users.
