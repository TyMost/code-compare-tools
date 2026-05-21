## 2024-05-21 - Icon-only Button Accessibility
**Learning:** Icon-only buttons often lack accessible names, making them difficult to use with screen readers. Providing an `aria-label` and `title` (for tooltips) significantly improves both accessibility and visual usability. In localized apps like this one, it is crucial to use the appropriate language for the labels to match the rest of the interface.
**Action:** Always check icon-only buttons for missing `aria-label` or `title` attributes and add localized descriptions when missing.
