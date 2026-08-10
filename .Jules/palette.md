## 2024-05-23 - Accessibility inside Vue v-for lists
**Learning:** Dynamic lists rendered with `v-for` that contain interactive controls frequently lack proper labels.
**Action:** When adding `aria-label` or `title` attributes inside Vue `v-for` loops, ensure uniqueness by using template literals that append the loop index or distinct item content (e.g., `:aria-label="\`删除规则 ${index + 1}\`"`) to aid screen reader distinction.
