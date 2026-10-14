# T09 measured contrast

Calculated from the actual Theme.kt foreground/background values using WCAG relative luminance: sRGB channels normalized to 0..1, linearized at 0.04045, then weighted 0.2126/0.7152/0.0722. Ratio is (lighter + 0.05)/(darker + 0.05).

| Combination | Foreground | Background | Contrast |
| --- | --- | --- | --- |
| light body | #17231D | #FFFFFF | 16.22:1 |
| light secondary | #56645B | #EAF0EB | 5.39:1 |
| light accent text | #286747 | #F7F8F5 | 6.31:1 |
| light on accent | #FFFFFF | #286747 | 6.73:1 |
| light error | #B3261E | #EAF0EB | 5.65:1 |
| light selected chip | #17231D | #D7EFDF | 13.36:1 |
| light chip border | #56645B | #F7F8F5 | 5.85:1 |
| light chart | #286747 | #DDE5DE | 5.23:1 |
| dark body | #E8EFEA | #1B241F | 13.62:1 |
| dark secondary | #AFBEB4 | #26372C | 6.51:1 |
| dark accent | #91D6AE | #111714 | 10.75:1 |
| dark on accent | #102B1D | #91D6AE | 8.98:1 |
| dark error | #FFB4AB | #26372C | 7.43:1 |
| dark selected chip | #E8EFEA | #234B35 | 8.44:1 |
| dark chip border | #AFBEB4 | #111714 | 9.38:1 |
| dark chart | #91D6AE | #34463A | 5.97:1 |

All tested normal-text pairs exceed 4.5:1. The explicit enabled chip border and chart fill/track pairs exceed 3:1. Category color dots and dividers are decorative; category names and direct chart values convey the information. Disabled controls are visually distinct and expose disabled state.

The unselected chip border previously used outlineVariant; it now explicitly uses outline. Screen-reader labels and text continue to identify each category and selected state.
