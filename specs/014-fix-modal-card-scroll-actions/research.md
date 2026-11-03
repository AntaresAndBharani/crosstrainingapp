# Phase 0 Research: Modal Card Auto-Scroll & Non-Blocking Accordion Actions

## 1. Root Cause of Dead Buttons After Adding Two Cycle Goals

- **Decision**: Eliminate the off-screen open-draft deadlock by combining (a) non-blocking accordion switching (`expandGoalDraftAt` auto-discards any open unsaved draft that has no valid target and restores `lastValidTargetWeight` on any previously valid goal whose target was cleared) and (b) deterministic viewport auto-scrolling whenever a card is added, expanded, or flagged with a validation error.
- **Rationale**:
  - Live reproduction on the Android 15 (`Pixel_10_API_35`) emulator confirmed that after adding and collapsing 2 goals in `CycleEditorDialog`, those 2 collapsed cards sit right at the bottom edge of the `AlertDialog` `verticalScroll` viewport (`y = 2090`).
  - Tapping `+ Add Movement` appends `Goal 3` below `Goal 2` in Expanded Edit State (`isCollapsed = false`). Because the first exercise in the catalog (`Air Bike`) has no historical best record, `Goal 3.targetWeight` initializes to `""` (`hasValidTarget = false`).
  - Without auto-scrolling, `Goal 3` is placed 100% below the `AlertDialog` scroll clip boundary (`y > 2090`), making the screen look completely unchanged after tapping `+ Add Movement`.
  - Worse, because `Goal 3` is open (`!isCollapsed`) and has `!hasValidTarget`, both `expandGoalDraftAt` and `appendMovementGoalDraft` hit `invalidOpenIndex = 2` (`Goal 3`) and return early without expanding `Goal 1`/`Goal 2` or adding a goal—making all buttons (`▾` on Goal 1, `▾` on Goal 2, and `+ Add Movement`) appear completely dead.
- **Alternatives Considered**:
  - *Only auto-scrolling without updating `expandGoalDraftAt`*: Rejected because if a user taps `+ Add Movement` (creating a blank `Goal 3`), scrolls back up, and taps `Goal 1` (`▾`) to inspect or edit `Goal 1`, blocking expansion of `Goal 1` because of an untouched blank draft is poor UX.

## 2. Deterministic Scroll Offset Resolution in `AlertDialog` Scrollable Columns

- **Decision**: Track each card's `topPx` and `heightPx` relative to the scrollable `Column` via `Modifier.onGloballyPositioned` (`coordinates.positionInParent().y` and `coordinates.size.height`), wait one layout frame (`withFrameNanos { }`) when a scroll request is triggered so the expanded/appended card is measured at its full height and `scrollState.maxValue` is updated, and animate `scrollState` to the target offset computed by `resolveGoalCardScrollOffset(...)`.
- **Rationale**:
  - Inside `Column(modifier = Modifier.verticalScroll(scrollState))`, every direct child's `positionInParent().y` reports its exact layout offset from the top of the `Column` content (`0 .. placeable.height`), and `scrollState.viewportSize` reports the exact visible viewport height of the `AlertDialog` body.
  - A pure helper function `resolveGoalCardScrollOffset(cardTopPx, cardHeightPx, viewportHeightPx, currentScrollPx, maxScrollPx, topPaddingPx)` can be unit-tested deterministically in `CyclesScreenTest` without requiring an emulator, while guaranteeing on-device smooth scrolling in `CycleEditorDialog` and `RoutineEditorDialog`.
- **Alternatives Considered**:
  - *`BringIntoViewRequester` alone*: `BringIntoViewRequester` called synchronously in `onClick` fires before the card expands or before `ScrollNode.measure` updates `maxValue` on the next frame, which can under-scroll when a card transitions from `70dp` (collapsed) to `380dp` (expanded). Combining frame-synchronized `onGloballyPositioned` bounds with `scrollState.animateScrollTo` guarantees the full expanded card height is accounted for.

## 3. Touch Target & Accessibility Semantics on `CollapsedGoalSummaryCard`

- **Decision**: Add explicit horizontal spacing (`Arrangement.spacedBy(4.dp)`) between the trailing `Remove goal` (`🗑`) and `Expand goal` (`▾`) `IconButton`s, and remove `mergeDescendants = true` from the outer `OutlinedCard` (keeping `contentDescription` on the card surface and distinct semantics on the child `IconButton`s).
- **Rationale**:
  - Emulator testing revealed that adjacent `36.dp` `IconButton`s with `0.dp` spacing caused taps near the inner edge of the expand chevron (`▾`) to hit the adjacent delete (`🗑`) button.
  - Merging all descendants into a clickable `OutlinedCard` swallowed the child `Remove goal` click semantics for accessibility services and semantic test matchers.
- **Alternatives Considered**:
  - *Removing the trailing `▾` `IconButton` and making it a non-clickable `Icon`*: While the card itself is clickable, users and UI tests specifically target `contentDescription = "Expand goal"` as an interactive button; keeping it as a spaced `IconButton` preserves full backward compatibility.
