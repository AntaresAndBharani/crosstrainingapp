/**
 * CrossTraining Analytics Lab — Google Sheets Auto-Sync Bridge
 *
 * Setup (one-time):
 * 1. Open Extensions -> Apps Script in your Google Sheet.
 * 2. Open Project Settings -> Script Properties and add:
 *      - EXPORT_URL   = https://<your-cloud-run-url>/export/sheets
 *      - EXPORT_TOKEN = <64-char token from Secret Manager: sheets-export-token>
 *    Optional Script Properties:
 *      - EXPORT_ENV        = production (default) | snapshot
 *      - SUBCYCLE_TYPE     = FAT_LOSS | RECOMPOSITION | MUSCLE_GAIN
 *      - CYCLE_START_DATE  = YYYY-MM-DD
 *      - CYCLE_FINISH_DATE = YYYY-MM-DD
 * 3. Paste this file into Code.gs, Save, select `syncCrossTrainingData`, and click Run once.
 */

function onOpen() {
  SpreadsheetApp.getUi()
    .createMenu("CrossTraining")
    .addItem("Sync Now", "syncCrossTrainingData")
    .addItem("Install Daily Auto-Sync Trigger", "installDailySyncTrigger")
    .addToUi();
}

function syncCrossTrainingData() {
  var props = PropertiesService.getScriptProperties();
  var baseUrl = (props.getProperty("EXPORT_URL") || "").trim();
  var token = (props.getProperty("EXPORT_TOKEN") || "").trim();

  if (!baseUrl || !token) {
    throw new Error(
      "Missing EXPORT_URL or EXPORT_TOKEN in Project Settings -> Script Properties. " +
        "Set EXPORT_URL to https://<cloud-run-url>/export/sheets and EXPORT_TOKEN to your sheets-export-token secret."
    );
  }

  var queryPairs = [];
  var optionalParams = [
    ["EXPORT_ENV", "env"],
    ["SUBCYCLE_TYPE", "subcycleType"],
    ["CYCLE_START_DATE", "cycleStartDate"],
    ["CYCLE_FINISH_DATE", "cycleFinishDate"],
  ];
  for (var i = 0; i < optionalParams.length; i++) {
    var propVal = (props.getProperty(optionalParams[i][0]) || "").trim();
    if (propVal) {
      queryPairs.push(
        encodeURIComponent(optionalParams[i][1]) + "=" + encodeURIComponent(propVal)
      );
    }
  }

  var fullUrl = baseUrl;
  if (queryPairs.length > 0) {
    fullUrl += (baseUrl.indexOf("?") >= 0 ? "&" : "?") + queryPairs.join("&");
  }

  var response = UrlFetchApp.fetch(fullUrl, {
    method: "get",
    headers: { Authorization: "Bearer " + token },
    muteHttpExceptions: true,
  });

  var code = response.getResponseCode();
  if (code !== 200) {
    throw new Error("CrossTraining export failed (HTTP " + code + "): " + response.getContentText());
  }

  var data = JSON.parse(response.getContentText());
  var ss = SpreadsheetApp.getActiveSpreadsheet();

  ensureDashboardTab_(ss);

  var weightsHeaders = [
    "date",
    "real_weight_kg",
    "is_interpolated",
    "avg_7d",
    "avg_14d",
    "avg_21d",
    "avg_28d",
    "weekly_body_weight_kg",
    "momentum_pct",
    "status",
    "interpolated_weight_kg",
    "entry_type",
    "weekly_rate_kg",
    "zone",
    "notes",
  ];
  var weightsRows = (data.weights || []).map(function (r) {
    return [
      r.date,
      r.real_weight_kg,
      r.is_interpolated,
      r.avg_7d,
      r.avg_14d,
      r.avg_21d,
      r.avg_28d,
      r.weekly_body_weight_kg,
      r.momentum_pct,
      r.status,
      r.interpolated_weight_kg,
      r.entry_type,
      r.weekly_rate_kg,
      r.zone,
      r.notes,
    ];
  });
  writeTab_(ss, "Weights", weightsHeaders, weightsRows, [1]);

  var sessionsHeaders = [
    "date",
    "session_id",
    "session_title",
    "routine_title",
    "item_type",
    "item_title",
    "exercise_name",
    "set_index",
    "reps",
    "weight_kg",
    "metric_value",
    "unit",
    "is_warmup",
    "is_failed",
    "cycle_id",
    "cycle_name",
  ];
  var sessionsRows = (data.sessions_sets || []).map(function (r) {
    return [
      r.date,
      r.session_id,
      r.session_title,
      r.routine_title,
      r.item_type,
      r.item_title,
      r.exercise_name,
      r.set_index,
      r.reps,
      r.weight_kg,
      r.metric_value,
      r.unit,
      r.is_warmup,
      r.is_failed,
      r.cycle_id,
      r.cycle_name,
    ];
  });
  writeTab_(ss, "Sessions_Sets", sessionsHeaders, sessionsRows, [1]);

  var prsHeaders = [
    "date",
    "movement_name",
    "rm_reps",
    "weight_kg",
    "source_complex_title",
    "session_title",
    "cycle_id",
    "cycle_name",
  ];
  var prsRows = (data.prs || []).map(function (r) {
    return [
      r.date,
      r.movement_name,
      r.rm_reps,
      r.weight_kg,
      r.source_complex_title,
      r.session_title,
      r.cycle_id,
      r.cycle_name,
    ];
  });
  writeTab_(ss, "PRs", prsHeaders, prsRows, [1]);

  var cyclesHeaders = [
    "cycle_id",
    "cycle_name",
    "is_active",
    "subcycle_type",
    "start_date",
    "finish_date",
    "target_min_pct_wk",
    "target_max_pct_wk",
    "goals_count",
    "sessions_count",
    "rep_maxes_count",
    "latest_activity_date",
  ];
  var cyclesRows = (data.cycles || []).map(function (r) {
    return [
      r.cycle_id,
      r.cycle_name,
      r.is_active,
      r.subcycle_type,
      r.start_date,
      r.finish_date,
      r.target_min_pct_wk,
      r.target_max_pct_wk,
      r.goals_count,
      r.sessions_count,
      r.rep_maxes_count,
      r.latest_activity_date,
    ];
  });
  writeTab_(ss, "Cycles", cyclesHeaders, cyclesRows, [5, 6, 12]);

  var goalsHeaders = [
    "goal_id",
    "cycle_id",
    "cycle_name",
    "is_active_cycle",
    "exercise_name",
    "goal_metric",
    "target_reps",
    "time_window_minutes",
    "start_value",
    "target_value",
    "current_best_value",
    "progress_pct",
    "notes",
  ];
  var goalsRows = (data.cycle_goals || []).map(function (r) {
    return [
      r.goal_id,
      r.cycle_id,
      r.cycle_name,
      r.is_active_cycle,
      r.exercise_name,
      r.goal_metric,
      r.target_reps,
      r.time_window_minutes,
      r.start_value,
      r.target_value,
      r.current_best_value,
      r.progress_pct,
      r.notes,
    ];
  });
  writeTab_(ss, "Cycle_Goals", goalsHeaders, goalsRows, []);

  var dailyLogsHeaders = [
    "date",
    "fast_completed",
    "is_rest_day",
    "calories_kcal",
    "protein_g",
    "carbs_g",
    "fat_g",
    "notes",
  ];
  var dailyLogsRows = (data.daily_logs || []).map(function (r) {
    return [
      r.date,
      r.fast_completed,
      r.is_rest_day,
      r.calories_kcal,
      r.protein_g,
      r.carbs_g,
      r.fat_g,
      r.notes,
    ];
  });
  writeTab_(ss, "Daily_Logs", dailyLogsHeaders, dailyLogsRows, [1]);

  SpreadsheetApp.flush();

  try {
    ss.toast(
      "Synced " +
        weightsRows.length +
        " weight days, " +
        sessionsRows.length +
        " sets, " +
        prsRows.length +
        " PRs, " +
        cyclesRows.length +
        " cycles.",
      "CrossTraining Sync Complete",
      5
    );
  } catch (_e) {
    // Headless time-driven triggers have no active UI; ignore toast failures.
  }
}

function installDailySyncTrigger() {
  var triggers = ScriptApp.getProjectTriggers();
  for (var i = 0; i < triggers.length; i++) {
    if (triggers[i].getHandlerFunction() === "syncCrossTrainingData") {
      ScriptApp.deleteTrigger(triggers[i]);
    }
  }
  ScriptApp.newTrigger("syncCrossTrainingData")
    .timeBased()
    .everyDays(1)
    .atHour(6)
    .create();

  try {
    SpreadsheetApp.getActiveSpreadsheet().toast(
      "Daily sync trigger installed (6am–7am).",
      "CrossTraining Trigger Ready",
      5
    );
  } catch (_e) {
    // Ignore when run outside UI context.
  }
}

function ensureDashboardTab_(ss) {
  if (!ss.getSheetByName("Dashboard")) {
    ss.insertSheet("Dashboard", 0);
  }
}

/**
 * Clears values in place (without deleting the tab) so Dashboard charts and pivot tables keep
 * their range references intact across refreshes. Converts null/undefined to "" for clean blanks.
 */
function writeTab_(ss, tabName, headers, rows, dateColumns) {
  var sheet = ss.getSheetByName(tabName);
  if (!sheet) {
    sheet = ss.insertSheet(tabName);
  }
  sheet.clearContents();

  var sanitizedRows = rows.map(function (row) {
    return row.map(toCell_);
  });
  var allRows = [headers].concat(sanitizedRows);
  sheet.getRange(1, 1, allRows.length, headers.length).setValues(allRows);
  sheet.setFrozenRows(1);
  sheet.getRange(1, 1, 1, headers.length).setFontWeight("bold");

  if (sanitizedRows.length > 0 && dateColumns && dateColumns.length > 0) {
    for (var i = 0; i < dateColumns.length; i++) {
      sheet
        .getRange(2, dateColumns[i], sanitizedRows.length, 1)
        .setNumberFormat("yyyy-mm-dd");
    }
  }
}

/**
 * Converts null/undefined to "" and escapes free-text strings that Sheets would otherwise parse
 * as formulas (e.g. notes such as "- felt heavy" or "=> PR" rendering as #ERROR!) by prefixing a
 * literal-text apostrophe. Numbers and booleans are written as-is.
 */
function toCell_(val) {
  if (val === null || val === undefined) return "";
  if (typeof val === "string" && /^[=+\-@]/.test(val)) return "'" + val;
  return val;
}
