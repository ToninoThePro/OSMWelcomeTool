## 2023-10-25 - Avoid Intermediate Lists in MainViewModel UIDs processing
**Learning:** Generating intermediate collections just to transform data into a distinct format causes unnecessary allocations (e.g. `changesets.map { it.uid }.distinct()`).
**Action:** Replaced sequential operations (`map`, `distinct`, `filterNot`) with a single `forEach` loop accumulating directly into a `Set`. This reduces memory pressure and execution time for hot paths.
