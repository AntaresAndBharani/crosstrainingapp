<#
.SYNOPSIS
    Static source-text hygiene assertions for summarize-unit-tests.ps1.
    Reads the script as text and asserts on counts/absences.
    Dot-sourced by Invoke-ScriptTests.ps1.
#>

$ScriptText = [System.IO.File]::ReadAllText(
    (Join-Path (Join-Path $PSScriptRoot "..") "summarize-unit-tests.ps1"),
    [System.Text.Encoding]::UTF8
)

Write-Host "--- static hygiene: summarize-unit-tests.ps1 ---"

# No Write-Error
Assert-True -Condition (-not ($ScriptText -match '\bWrite-Error\b')) `
            -TestName "static: no Write-Error calls"

# Every 'exit' occurrence must be 'exit 0' (three intentional early/normal returns)
$exitMatches   = [regex]::Matches($ScriptText, '\bexit\b\s+(\S+)')
$badExits      = @($exitMatches | Where-Object { $_.Groups[1].Value -ne '0' })
$totalExits    = $exitMatches.Count
Assert-True   -Condition ($badExits.Count -eq 0) `
              -TestName "static: all exit statements are 'exit 0' (no non-zero exits)" `
              -FailureMessage "Found exit with non-zero code(s): $($badExits | ForEach-Object { $_.Value } | Out-String)"
Assert-True   -Condition ($totalExits -eq 3) `
              -TestName "static: exactly 3 exit statements (early no-results x2, normal end x1)" `
              -FailureMessage "Expected 3 exit statements, found $totalExits"

# No $ErrorActionPreference assignment
Assert-True -Condition (-not ($ScriptText -match '\$ErrorActionPreference\s*=')) `
            -TestName "static: no `$ErrorActionPreference assignment"

# Sticky-comment marker defined exactly once
$markerDefinitions = [regex]::Matches($ScriptText, '\$EvidenceMarker\s*=')
Assert-True -Condition ($markerDefinitions.Count -eq 1) `
            -TestName "static: EvidenceMarker defined exactly once" `
            -FailureMessage "Expected 1 definition of `$EvidenceMarker, found $($markerDefinitions.Count)"

# The marker value itself contains the expected string
Assert-True -Condition ($ScriptText.Contains('<!-- unit-test-evidence -->')) `
            -TestName "static: EvidenceMarker contains '<!-- unit-test-evidence -->'"

# No assignment to automatic variable $matches
Assert-True -Condition (-not ($ScriptText -match '\$matches\s*=')) `
            -TestName "static: no `$matches assignment (PSAvoidAssignmentToAutomaticVariable)"

# ArtifactName fallback guard present
Assert-True -Condition ($ScriptText -match '\[string\]::IsNullOrWhiteSpace\(\$ArtifactName\)') `
            -TestName "static: ArtifactName IsNullOrWhiteSpace guard present"

# No leftover throwaway test comments (#178)
Assert-True -Condition (-not ($ScriptText -match '#\s*TEST:')) `
            -TestName "static: no throwaway TEST comments"



