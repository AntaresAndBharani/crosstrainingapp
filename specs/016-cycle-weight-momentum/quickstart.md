# Quickstart & Validation Guide: Universal Cycle Weight Tracking & Momentum Engine

**Feature**: `016-cycle-weight-momentum`
**Date**: 2026-11-09

## Prerequisites
- JDK 17 configured for Gradle (`.\gradlew.bat`)
- Node.js 20+ for `mcp-server/` unit tests

## 1. Run Android Unit Tests
Verifies linear daily interpolation (`REAL` vs `THEORETICAL`), cumulative cold-start and lookback horizons (`7D`, `14D`, `21D`, `28D`), `3:4:2:1` Weekly Body Weight composite, Momentum `%` classification, dynamic finish weight compounding, `Cycle.subcycleType` metadata encoding, and UI state resolvers:
```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
```

## 2. Run PowerShell Script Regression Suite
```powershell
.\scripts\tests\Invoke-ScriptTests.ps1
```

## 3. Run MCP Server Unit Tests
Verifies the Coaching MCP Server weight momentum & interpolation engine:
```powershell
Push-Location mcp-server
npm test
Pop-Location
```

## 4. Run Android Lint & Build Debug APK
```powershell
.\gradlew.bat lintDebug assembleDebug --no-daemon
Copy-Item -Path "app\build\outputs\apk\debug\app-debug.apk" -Destination "local_test\latest.apk" -Force
```
