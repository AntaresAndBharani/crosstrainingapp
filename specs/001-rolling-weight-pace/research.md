# Phase 0 Research: Rolling Window Average Pacing & Goal Forecast Alignment

**Feature**: `001-rolling-weight-pace`
**Date**: 2026-10-22

## 1. Root Cause Analysis of 36.6-Week Projection Defect

### Decision
Replace raw single-day point-to-point anchor comparisons in `FatLossAnalytics.computePaceRate` (`compute7DayPace` and `compute14DayPace`) with 7-day rolling window average comparisons ($\overline{W}_{7\text{d}}(t)$ vs $\overline{W}_{7\text{d}}(t - 7\text{d})$ for 7-Day Pace, and $\overline{W}_{7\text{d}}(t)$ vs $\overline{W}_{7\text{d}}(t - 14\text{d})$ divided by 2 for 14-Day Pace), and drive `computePaceForecast` from tiered smoothed velocity.

### Rationale
In the production codebase (`FatLossAnalytics.kt`):
- `computeRollingWeightHero` computes 7-day and 14-day rolling averages for the top 3-pillar display (`TODAY 75.7 kg`, `LAST WEEK 75.9 kg (-0.3 kg)`, `14D AVG 75.8 kg (-0.2 kg)`).
- However, `computePaceRate` still computes `((newest.weightKg - anchor.weightKg) / elapsedDays) * 7.0` using single raw weigh-ins on `t` and `t - windowDays`.
- When an athlete weighs less on Thursdays than on other days of the week (e.g., `74.9 kg` two Thursdays ago, `74.8 kg` last Thursday, `74.7 kg` today) while their full 7-day rolling average drops from `~76.2 kg` to `75.94 kg` to `75.66 kg` (`-0.28 kg/wk`), the raw point-to-point difference only sees `(74.7 - 74.9) / 2 = -0.10 kg/wk`.
- `computePaceForecast` then takes the 7-day average remaining weight (`75.7 - 72.0 = 3.7 kg`) and divides it by the raw 14-day velocity (`0.101 kg/wk`), producing `36.6 weeks`.
- Using 7-day rolling window averages for both `compute7DayPace` and `compute14DayPace` guarantees that `7-Day Pace` matches the `LAST WEEK` pillar delta (`-0.3 kg/wk`) and `14-Day Pace` reflects the true 2-week smoothed trend, bringing the forecast to a realistic ~12–13 weeks.

### Alternatives Considered
- **Exact raw same-weekday pace + raw forecast**: Rejected because a single dehydrated or high-sodium reading 7 or 14 days ago still skews the 14-day pace and forecast, and contradicts the 7D average shown in the `TODAY` and `LAST WEEK` hero pillars.
- **14D average vs lagged 14D average for 14-Day Pace**: Rejected because `[t-13, t]` and `[t-20, t-7]` overlap by 7 days (`[t-13, t-7]`), which measures a 1-week shift of a 14-day window rather than a true 14-day span between disjoint 7-day windows `[t-6, t]` and `[t-20, t-14]`.

---

## 2. Pre-Cycle Historical Lookback Continuity

### Decision
Include non-deleted pre-cycle `WeightEntry` records (`deletedAtMillis == null && !date.isAfter(referenceDate)`) when evaluating rolling lookback windows (`[t-13, t-7]`, `[t-20, t-14]`, `[t-13, t]`, `[t-20, t-7]`), provided at least one active in-cycle entry (`!date.isBefore(cycleStartDate)`) exists. When a historical lookback window has no logged entries at all, fall back to `cycle.startingWeightKg` if `cycleStartDate` is on or before the lookback window's end date.

### Rationale
- Previously, `FatLossAnalytics.prepareChartSeries` in `WeightAnalytics.kt` was fixed (Issue #558) to allow pre-period lookback for moving averages, but `FatLossAnalytics.computeRollingWeightHero` and `computePaceRate` still filtered out `date < cycleStartDate` before computing lookback windows.
- For a cycle started 2–3 weeks ago (e.g., 28 Sept), `[t-20, t-14]` or `t-7`'s prior 14-day pace lookback `[t-27, t-21]` extends prior to `cycleStartDate`. Including pre-cycle entries allows 7-Day and 14-Day Pace and their week-over-week comparison subtitles to work immediately in weeks 1–3 of a new cycle.
- If a user is brand new and has no pre-cycle entries, falling back to `startingWeightKg` (or `resolveAnchorEntry` for sparse gaps) preserves cold-start behavior without regressions.

### Alternatives Considered
- **Strictly filtering `date >= cycleStartDate` for all lookback windows**: Rejected because it forces 14-Day Pace to remain blank for the first 14 days and 14-Day Pace comparison subtitles to remain uncomputed for the first 21 days of every cycle, even for veteran users with months of daily logs.

---

## 3. Goal Forecast Velocity Tiering Hierarchy

> **Revised after PR #606 review**: The sign-based fallback below was replaced by availability-based selection (`v14 ?: v7`, then `Stalled` if non-positive), and Tier 3 was removed. Falling through on sign let the forecast project an arrival date from the 7D or cycle pace while the displayed 14D pace card showed a gain, contradicting this feature's goal of aligning the forecast with the pace cards. Tier 3 became unreachable once tiers are chosen by availability, because the 7D pace always resolves after 7 days since the start baseline. The original decision is kept below for history.

### Decision
Resolve the active deficit velocity $v_{\text{active}}$ in `computePaceForecast` using a 3-tier smoothed fallback hierarchy:
1. **Tier 1 (Primary)**: 14-Day Smoothed Pace velocity ($v_{14\text{d}} = -P_{14\text{d}}(t)$) if non-null and $\text{round1}(v_{14\text{d}}) > 0.0$.
2. **Tier 2 (Fallback 1)**: 7-Day Smoothed Pace velocity ($v_{7\text{d}} = -P_{7\text{d}}(t)$) if Tier 1 is null or $\le 0.0$, and $\text{round1}(v_{7\text{d}}) > 0.0$.
3. **Tier 3 (Fallback 2)**: Overall Cycle Pace velocity ($v_{\text{cycle}} = \frac{W_{\text{start}} - \overline{W}_{7\text{d}}(t)}{\text{elapsedDays}} \times 7.0$) over $\text{elapsedDays} \ge 7$ if Tiers 1 and 2 are null or $\le 0.0$, and $\text{round1}(v_{\text{cycle}}) > 0.0$.
4. **Stalled**: If all three tiers evaluate to $\le 0.0$ (or null after passing the 7-day history span guard), return `PaceForecast.Stalled`.

### Rationale
- Previously, if `anchor14` existed (even with a 0.0 or slightly positive rate due to noise 14 days ago), `anchor7` was completely skipped (`val anchor7 = if (anchor14 == null) ... else null`), causing the forecast to immediately report `Stalled` or `> 1 year` even when the athlete's 7-day pace and overall cycle pace were strongly negative (losing weight).
- Allowing fallback from Tier 1 $\rightarrow$ Tier 2 $\rightarrow$ Tier 3 when an earlier tier is either unavailable (`null`) or non-deficit ($\le 0.0$) ensures that an athlete who is actively losing weight over the last 7 days or across the cycle still receives a meaningful projection.
- Conversely, if the athlete has gained weight across 14D, 7D, and the cycle (all tiers $\le 0.0$), `PaceForecast.Stalled` is still accurately returned.

### Alternatives Considered
- **Only falling back to Tier 2 when Tier 1 is `null`**: Rejected because a temporary 14-day plateau followed by a strong 7-day drop would still show "Pace stalled" despite active 7-day weight loss.

---

## 4. Top Reference Track Arithmetic Alignment

### Decision
Update `WeightProgressionHeroCard` in `ProgressScreen.kt` to compute `progressFraction` (`% to goal`) and `remainingKg` (`-X.X kg left`) using `resolvedToday7dAvgKg` rather than raw `currentWeightKg`.

### Rationale
- The centerpiece of `WeightProgressionHeroCard` displays `TODAY` as `resolvedToday7dAvgKg` (e.g., `75.7 kg`), and `computePaceForecast` also computes `remainingKg` from the 7-day average (`75.7 - 72.0 = 3.7 kg`).
- Using raw `currentWeightKg` (`74.7 kg`) in the top right `GOAL 72.0 kg (-2.7 kg left)` created a 1.0 kg contradiction between `TODAY 75.7 kg` and `GOAL 72.0 kg (-2.7 kg left)` on the exact same card.
- Aligning the top reference track with `resolvedToday7dAvgKg` ensures `START`, `TODAY`, `GOAL (-X.X kg left)`, `% to goal`, and `Estimated: X.X weeks` are 100% consistent.

### Alternatives Considered
- **Reverting `TODAY` to raw weight**: Rejected because the 3-pillar rolling average hero was intentionally introduced in Issue #602 to smooth daily water fluctuations.
