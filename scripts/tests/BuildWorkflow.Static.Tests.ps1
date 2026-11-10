<#
.SYNOPSIS
    Static source-text regression assertions for .github/workflows/build.yml.
    Verifies script-tests, mcp-server-tests (Node 22 + npm ci/build/test), and Android unit test jobs.
    Dot-sourced by Invoke-ScriptTests.ps1.
#>

$BuildWorkflowPath = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot "../../.github/workflows/build.yml")
)

Write-Host "--- static hygiene: build.yml ---"

Assert-True -Condition ([System.IO.File]::Exists($BuildWorkflowPath)) `
            -TestName "static: build.yml exists"

$BuildWorkflowText = [System.IO.File]::ReadAllText($BuildWorkflowPath, [System.Text.Encoding]::UTF8)

Assert-True -Condition ($BuildWorkflowText -match 'name:\s*PR CI & Test Verification') `
            -TestName "static: build.yml defines 'PR CI & Test Verification' workflow name"

Assert-True -Condition ($BuildWorkflowText -match 'script-tests:' -and $BuildWorkflowText -match '\./scripts/tests/Invoke-ScriptTests\.ps1') `
            -TestName "static: build.yml runs Invoke-ScriptTests.ps1 in script-tests job"

Assert-True -Condition ($BuildWorkflowText -match 'mcp-server-tests:' -and $BuildWorkflowText -match 'actions/setup-node@v4' -and $BuildWorkflowText -match "node-version:\s*'22'") `
            -TestName "static: build.yml defines mcp-server-tests job on Node.js 22"

Assert-True -Condition ($BuildWorkflowText -match 'working-directory:\s*mcp-server' -and $BuildWorkflowText -match 'npm ci' -and $BuildWorkflowText -match 'npm run build' -and $BuildWorkflowText -match 'npm test') `
            -TestName "static: build.yml executes npm ci, npm run build, and npm test in mcp-server"

Assert-True -Condition ($BuildWorkflowText -match '\./gradlew testDebugUnitTest --stacktrace') `
            -TestName "static: build.yml executes Android testDebugUnitTest"
