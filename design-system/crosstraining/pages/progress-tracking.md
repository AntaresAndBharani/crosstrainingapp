# Page Override: Progress & Tracking Pages (`progress-tracking`)

> ⚠️ **IMPORTANT:** Rules in this file **override** [`MASTER.md`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/design-system/crosstraining/MASTER.md) for CrossTraining's tracking and analytics surfaces.
> Only deviations and tracking-specific guidelines are documented here. For all global tokens, themes, and architecture standards, refer to `MASTER.md`.

---

## 1. Tracking Surface Inventory

Primary files covered by this override:
- [`ProgressScreen.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/ProgressScreen.kt)
  - Modes (`ProgressMode`): `BY_DAILY_ROUTINE` ("By routine"), `BY_ROUTINE` ("By complex"), `BY_EXERCISE` ("By exercise"), `CYCLE_GOALS` ("Cycle goals"), `FAT_LOSS` ("Fat loss"), `BODY_WEIGHT` ("Body weight").
  - Shared exercise/routine/complex sections: `DailyRoutineProgressSection` (`DailyRoutineConstituentOverviewCard`, `DailyRoutineConstituentDrillDown`), `RoutineProgress`, `ExerciseProgress` (including Rep-max progression & Personal records).
  - Shared tracking components in `ProgressScreen.kt`: `KpiRows`, `KpiCard`, `EvolutionCards`, `BlockHistoryCard`, `BlockHistoryRow`, `DeltaText`, `TableHeader`.
- [`HistoryScreen.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/screens/HistoryScreen.kt)
  - `CompactSessionCard` and expandable session/block/set summaries.
- [`LineChart.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/ui/components/LineChart.kt)
  - `LineChart`, `MultiLineChart`, `ChartPoint`, `ChartSeries`.
- **Body Weight Data Source:** [`WeightEntry.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/model/WeightEntry.kt) (`date: LocalDate`, `weightKg: Double`, `deletedAtMillis: Long?`) via [`WeightDao.kt`](file:///c:/Users/rogal/workspaces/ws-gym/crosstrainingapp/app/src/main/java/com/fractanomics/crosstraining/data/dao/WeightDao.kt).

---

## 2. Density & Layout Rhythm Overrides (`Density: 7/10`)

Tracking pages use a tighter analytical rhythm than general forms while preserving readability and touch ergonomics:
- **Section Rhythm:** `14.dp` vertical spacing between tracking sections inside `ProgressScreen` (`Arrangement.spacedBy(14.dp)`).
- **2-Column KPI Grid (`KpiRows` / `KpiCard`):** `10.dp` horizontal gap between paired `OutlinedCard` KPI tiles; `12.dp` inner card padding; `2.dp` vertical gap between label, hero value, and delta/subtext.
- **Filter Rows:** Mode and constituent `FilterChip` strips use `8.dp` spacing (`LazyRow` / `FlowRow`) so options stay scannable without accidental taps.
- **History Cards:** `14.dp` rounded corners on `ElevatedCard` (`CompactSessionCard`, `DailyRoutineConstituentOverviewCard`), `12.dp`–`14.dp` internal padding, `8.dp` vertical row padding inside `BlockHistoryRow`.

---

## 3. Charts & Data Visualization (`--domain chart`)

Guided by `ui-ux-pro-max` `--domain chart` (*Trend Over Time*):
1. **Chart Selection:** Use `LineChart` / `MultiLineChart` (`ChartPoint`, `ChartSeries`) for date-indexed strength, volume, and body-weight-relative trends.
2. **Low-Data Guardrail (`< 4` points):** When fewer than 4 logged dates exist (and especially for 0–1 points), do not rely on a chart alone to tell the story—KPI stat cards (`KpiRows`) and history rows must carry the primary summary cleanly (`LineChart` / `EvolutionCards` already guard empty/short series).
3. **Color + Non-Color Differentiation:** Distinguish multiple chart series using distinct `MaterialTheme.colorScheme` tokens (`primary`, `tertiary`, `secondary`) paired with an explicit legend (`MultiLineChart`) and descriptive series names—never rely on hue alone.
4. **Accessible Tabular Fallback:** Every chart section MUST remain paired with a visible, accessible tabular/list representation (such as `BlockHistoryCard` / `BlockHistoryRow` or a structured breakdown list) below it so screen-reader and precision users can inspect exact per-date numbers.
5. **Axis & Zero-NaN Safety:** Guard empty, null, or flat (`minV == maxV`) value series cleanly with zero `NaN` propagation (consistent with `ChartCanvas`).

---

## 4. Compact Ratios, Badges & Inline Disclosure (`--domain ux`)

Guided by `ui-ux-pro-max` `--domain ux` (*Compact Label Overflow*, *Contextual Live Badge Updates*):
1. **Single-Line Numeric Pills:** Compact metric pills, trend chips, and percentage/index badges MUST format numbers with `fontFeatureSettings = "tnum"` and stay single-line (`maxLines = 1, softWrap = false`) where bounded—never wrap a compact badge unpredictably onto 2 ragged lines.
2. **Reflow Before Clipping (`fontScale >= 1.3f`):** On narrow `360dp` phones or large `fontScale`, move secondary badges/labels onto a dedicated second line below the title/primary figure rather than truncating numbers (`...`) or squeezing columns.
3. **No Hover-Only Tooltips:** Mobile users cannot hover. When showing a derived ratio or index (e.g., lifted weight divided by daily body weight), expose its context (numerator, denominator/body weight, date alignment, or formula hint) via inline secondary subtext, expandable row details (`BlockHistoryRow` / `CompactSessionCard`), or merged TalkBack semantics.
4. **Explicit Missing / Non-Applicable States:** Never render `NaN`, `Infinity`, or misleading `0%` when a day lacks a logged body weight or when a movement is non-weighted (`CALORIES`, `TIME`, `DISTANCE`, bodyweight-only `REPS`). Render `—` with a concise contextual sub-label (e.g., `"No body weight logged"`) and descriptive TalkBack phrasing.

---

## 5. Deltas, State Retention & Performance (`--stack jetpack-compose`)

1. **Delta Formatting:** Follow `DeltaText` conventions—pair `▲` / `▼` / `=` directional cues with semantic `MaterialTheme.colorScheme` colors (`tertiary` for positive, `error` for negative, `onSurfaceVariant` for flat/first session) and consistent unit/suffix formatting (`%`, `x BW`, `kg`, `lbs`).
2. **Display-Unit Consistency:** When computing ratios or displaying lifted vs body weight, convert or compute in consistent units (matching `weightKg` on both sides before deriving `%`, or using `BigDecimal` `HALF_UP` rounding for displayed values) so `kg` and `lbs` modes never distort a dimensionless percentage.
3. **State Retention:** Persist tracking view toggles (selected metric/display mode, unit toggle, timeframe/filter choice) with `rememberSaveable` (or `AppViewModel` `StateFlow`) across configuration changes.
4. **Recomposition Hygiene:** Compute date-to-body-weight lookup maps and derived series inside `remember(...)` or domain analytics helpers (`WeightAnalytics`, `DailyRoutineCodec`, `AppViewModel`)—never perform full list scans or joins inside tight Compose row loops without memoization.
