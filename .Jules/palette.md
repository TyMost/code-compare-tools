## 2024-08-04 - Dynamic List Accessibility
**Learning:** When auditing Vue components for accessibility, dynamic lists rendered with `v-for` that contain interactive controls frequently lack proper labels. In Vue 2 with Element UI, adding `aria-label` or `title` attributes inside `v-for` loops requires ensuring uniqueness.
**Action:** Use template literals that append the loop index or distinct item content (e.g., `:aria-label="\`删除规则 ${index + 1}\`"`) to aid screen reader distinction and provide contextual tooltips.
