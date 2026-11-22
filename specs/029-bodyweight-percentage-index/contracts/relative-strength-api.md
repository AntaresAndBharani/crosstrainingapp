# Contract: Relative Strength (`% BW`) Calculation & MCP Read Parity

## 1. Arithmetic & Rounding Contract

1. **Conversion to Kilograms:**
   - If `unit` equals `"lb"` or `"lbs"` (case-insensitive, trimmed): `liftedKg = liftedWeight / 2.20462262185`.
   - If `unit` equals `"kg"`: `liftedKg = liftedWeight`.
   - Any other unit (`"cal"`, `"s"`, `"m"`, `"reps"`, etc.): not weighted → returns `null`.
2. **Formula:**
   $$\text{percentBw} = \text{roundHalfUp}_1\left(\frac{\text{liftedKg} \times 100}{\text{bodyWeightKg}}\right)$$
   - Android uses `java.math.BigDecimal` with `RoundingMode.HALF_UP` at scale `1`.
   - TypeScript (`mcp-server`) mirrors exact `HALF_UP` scale `1` using decimal string/integer rounding (`Math.round((num + 1e-12) * 10) / 10` for positive finite values).
3. **Display Formatting:**
   - `null` → `"—"`
   - Whole number (e.g. `140.0`) → `"140% BW"` (or `"~140% BW"` when `isEstimated`)
   - Fractional (e.g. `142.5`) → `"142.5% BW"` (or `"~142.5% BW"` when `isEstimated`)
4. **Delta Reconciliation:**
   - Given `latestPct` and `prevPct` (both already rounded to scale `1`), `delta = roundHalfUp1(latestPct - prevPct)`.

## 2. Date Resolution Contract
- Input `weightEntries` filtered to `deletedAtMillis == null && weightKg > 0 && isFinite(weightKg)`.
- Target date `D`:
  1. Entry on `D` → `EXACT` (`isEstimated = false`, `daysPrior = 0`)
  2. Latest entry in `[D - 7 days, D - 1 day]` → `ESTIMATED_PRIOR` (`isEstimated = true`, `daysPrior = 1..7`)
  3. None found → `UNAVAILABLE` (`weightKg = null`)

## 3. MCP Tool Response Examples (Additive Fields)

### `list_rep_maxes` Item
```json
{
  "id": 12,
  "exerciseId": 3,
  "exerciseName": "Squat Snatch",
  "reps": 1,
  "weight": 95,
  "date": "2026-05-10",
  "percentBw": 126.7,
  "bodyWeightKg": 75,
  "bodyWeightDate": "2026-05-10",
  "isBodyWeightEstimated": false
}
```

### `get_daily_routine_progress` Weighted Constituent Excerpt
```json
{
  "title": "Snatch Complex",
  "unit": "kg",
  "allTimeTop": 100,
  "allTimeTopPercentBw": 117.6,
  "allTimeTopPercentBwEstimated": false,
  "peakTopPercentBw": 126.7,
  "peakTopPercentBwDate": "2026-05-10",
  "peakTopPercentBwRawKg": 95,
  "lastTop": 95,
  "lastTopPercentBw": 126.7,
  "lastTopPercentBwEstimated": false,
  "lastAveragePercentBw": 120,
  "lastVolumePercentBw": 593.3,
  "topPercentBwDelta": 3.8,
  "bodyWeightCoverage": "6/7"
}
```
