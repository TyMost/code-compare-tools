## YYYY-MM-DD - [Added aria-label to icon-only button]
**Learning:** Found an icon-only button (`<i class="el-icon-delete"></i>` within `<el-button>`) used for removing custom exclude patterns in `DiffMatrixFilters.vue`. Icon-only buttons without accessible names are a common accessibility anti-pattern.
**Action:** Added `aria-label="删除规则"` to provide screen readers with a description of the button's function. In the future, I will proactively search for other instances of `<el-button>` that only contain `<i>` tags and ensure they have appropriate `aria-label` or `title` attributes.
