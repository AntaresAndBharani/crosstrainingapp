[Diagnostics.CodeAnalysis.SuppressMessageAttribute('PSAvoidUsingWriteHost', '', Justification = 'Human-readable CI console output')]
param (
    [string]$ResultsDir = "app/build/test-results/testDebugUnitTest",
    [string]$OutFile = "unit-test-summary.md",
    [string]$PrNumber = "",
    [string]$Repo = "",
    [string]$ArtifactName = ""
)

$artifactNameFallbackApplied = $false
if ([string]::IsNullOrWhiteSpace($ArtifactName)) {
    $ArtifactName = "unit test report"
    $artifactNameFallbackApplied = $true
}

$EvidenceMarker = "<!-- unit-test-evidence -->"

. (Join-Path $PSScriptRoot 'lib/PrComment.ps1')

function ConvertTo-RelativePath ([string]$text) {
    if (-not $text) { return "" }

    # Strip known absolute path prefixes
    if ($env:GITHUB_WORKSPACE) {
        $text = $text.Replace($env:GITHUB_WORKSPACE + "/", "").Replace($env:GITHUB_WORKSPACE + "\", "").Replace($env:GITHUB_WORKSPACE, "")
    }

    # Strip Linux CI runner paths (/home/runner/work/repo/repo/...)
    $text = [regex]::Replace($text, '(?i)/home/runner/work/[^/\r\n]+/[^/\r\n]+/', '')

    # Strip Windows CI runner paths (D:\a\repo\repo\...)
    $text = [regex]::Replace($text, '(?i)[a-zA-Z]:[\\/]a[\\/][^\\/\r\n]+[\\/][^\\/\r\n]+[\\/]', '')

    # Strip local workspace paths
    $text = [regex]::Replace($text, '(?i)[a-zA-Z]:[\\/](?:[^\\/\r\n]+[\\/])+crosstrainingapp[\\/]', '')

    return $text
}

function Limit-TextLines ([string]$text, [int]$maxLines = 40) {
    if (-not $text) { return "" }

    $lines = $text -split '\r?\n'
    if ($lines.Count -gt $maxLines) {
        $truncatedLines = $lines[0..($maxLines - 1)]
        return ($truncatedLines -join "`n") + "`n... (truncated)"
    }
    return $lines -join "`n"
}

function Format-StackTrace ([string]$trace) {
    if (-not $trace) { return "" }

    $trace = ConvertTo-RelativePath -text $trace
    $trace = Limit-TextLines -text $trace -maxLines 40

    return $trace.Trim()
}

function Get-BacktickFence ([string]$text, [int]$MinLength = 3) {
    $minFence = '`' * [Math]::Max(1, $MinLength)
    if (-not $text) { return $minFence }
    $backtickMatches = [regex]::Matches($text, '`+')
    $maxRun = 0
    if ($backtickMatches.Count -gt 0) {
        $maxRun = ($backtickMatches | Measure-Object -Property Length -Maximum).Maximum
    }
    $fenceLen = [Math]::Max($MinLength, $maxRun + 1)
    return ('`' * $fenceLen)
}

function Format-FailureMessage ([string]$message) {
    if (-not $message) { return "" }

    $message = ConvertTo-RelativePath -text $message
    $message = Limit-TextLines -text $message -maxLines 40

    if ($message -match '\r?\n') {
        $fence = Get-BacktickFence -text $message
        return "**Message:**`n$fence`n$message`n$fence`n`n"
    } else {
        $delim = Get-BacktickFence -text $message -MinLength 1
        if ($message.StartsWith('`') -or $message.EndsWith('`')) {
            $content = " $message "
        } else {
            $content = $message
        }
        return "**Message:** $delim$content$delim`n`n"
    }
}


function Write-SummaryOutput ([string]$content) {
    if (-not [string]::IsNullOrWhiteSpace($OutFile)) {
        try {
            $outDir = [System.IO.Path]::GetDirectoryName($OutFile)
            if (-not [string]::IsNullOrWhiteSpace($outDir) -and -not (Test-Path -Path $outDir)) {
                [System.IO.Directory]::CreateDirectory($outDir) | Out-Null
            }
            [System.IO.File]::WriteAllText($OutFile, $content, (New-Object System.Text.UTF8Encoding $false))
        } catch {
            Write-Warning "Failed to write summary to '$OutFile': $_"
        }
    }

    if ($env:GITHUB_STEP_SUMMARY) {
        try {
            [System.IO.File]::AppendAllText($env:GITHUB_STEP_SUMMARY, "`n" + $content + "`n", (New-Object System.Text.UTF8Encoding $false))
        } catch {
            Write-Warning "Failed to append summary to GITHUB_STEP_SUMMARY: $_"
        }
    }
}

$xmlFiles = @()
if (-not [string]::IsNullOrWhiteSpace($ResultsDir) -and (Test-Path -Path $ResultsDir)) {
    $xmlFiles = @(Get-ChildItem -Path $ResultsDir -Filter "TEST-*.xml" -File -ErrorAction SilentlyContinue)
}

if ($xmlFiles.Count -eq 0) {
    $NoResultsBody = "$EvidenceMarker`n### :test_tube: Unit Test Results`n`nNo unit test results found.`n"
    Write-SummaryOutput -content $NoResultsBody
    Publish-PrComment -Repo $Repo -PrNumber $PrNumber -Marker $EvidenceMarker -Body $NoResultsBody -TempFilePrefix 'unit-test-body'
    exit 0
}

$TotalTests = 0
$TotalFailures = 0
$TotalSkipped = 0
$TotalTime = 0.0
$FailuresList = [System.Collections.Generic.List[PSCustomObject]]::new()
$SuitesProcessed = 0

foreach ($file in $xmlFiles) {
    try {
        $rawXml = Get-Content -Path $file.FullName -Raw
        if ([string]::IsNullOrWhiteSpace($rawXml)) { continue }
        [xml]$xml = $rawXml
    } catch {
        continue
    }

    if ($null -eq $xml) {
        continue
    }

    $suites = $xml.SelectNodes("//testsuite")
    if ($null -eq $suites -or $suites.Count -eq 0) {
        continue
    }

    $SuitesProcessed += $suites.Count

    foreach ($suite in $suites) {
        $suiteTests = 0
        $suiteFailures = 0
        $suiteErrors = 0
        $suiteSkipped = 0
        $suiteTime = 0.0

        if ($suite.Attributes["tests"]) { [int]::TryParse($suite.Attributes["tests"].Value, [ref]$suiteTests) | Out-Null }
        if ($suite.Attributes["failures"]) { [int]::TryParse($suite.Attributes["failures"].Value, [ref]$suiteFailures) | Out-Null }
        if ($suite.Attributes["errors"]) { [int]::TryParse($suite.Attributes["errors"].Value, [ref]$suiteErrors) | Out-Null }
        if ($suite.Attributes["skipped"]) { [int]::TryParse($suite.Attributes["skipped"].Value, [ref]$suiteSkipped) | Out-Null }
        if ($suite.Attributes["time"]) { [double]::TryParse($suite.Attributes["time"].Value, [System.Globalization.NumberStyles]::Any, [System.Globalization.CultureInfo]::InvariantCulture, [ref]$suiteTime) | Out-Null }

        $TotalTests += $suiteTests
        $TotalFailures += ($suiteFailures + $suiteErrors)
        $TotalSkipped += $suiteSkipped
        $TotalTime += $suiteTime

        $testcases = $suite.SelectNodes(".//testcase")
        if ($null -ne $testcases) {
            foreach ($tc in $testcases) {
                $failNode = $tc.SelectSingleNode("failure | error")
                if ($null -ne $failNode) {
                    $className = if ($tc.Attributes["classname"]) { $tc.Attributes["classname"].Value } else { "" }
                    $methodName = if ($tc.Attributes["name"]) { $tc.Attributes["name"].Value } else { "" }
                    $rawMessage = if ($failNode.Attributes["message"]) { $failNode.Attributes["message"].Value } else { "" }
                    $message = $rawMessage
                    $rawTrace = $failNode.InnerText
                    $formattedTrace = Format-StackTrace -trace $rawTrace

                    $FailuresList.Add([PSCustomObject]@{
                        ClassName = $className
                        MethodName = $methodName
                        Message = $message
                        StackTrace = $formattedTrace
                    })
                }
            }
        }
    }
}

if ($SuitesProcessed -eq 0) {
    $NoResultsBody = "$EvidenceMarker`n### :test_tube: Unit Test Results`n`nNo unit test results found.`n"
    Write-SummaryOutput -content $NoResultsBody
    Publish-PrComment -Repo $Repo -PrNumber $PrNumber -Marker $EvidenceMarker -Body $NoResultsBody -TempFilePrefix 'unit-test-body'
    exit 0
}

$TotalPassed = $TotalTests - $TotalFailures - $TotalSkipped
if ($TotalPassed -lt 0) { $TotalPassed = 0 }

$ElapsedFormatted = "$($TotalTime.ToString("0.00", [System.Globalization.CultureInfo]::InvariantCulture))s"

$Body = "$EvidenceMarker`n### :test_tube: Unit Test Results`n`n"
$Body += "| Total | Passed | Failed | Skipped | Elapsed |`n"
$Body += "|---|---|---|---|---|`n"
$Body += "| $TotalTests | $TotalPassed | $TotalFailures | $TotalSkipped | $ElapsedFormatted |`n"

if ($TotalFailures -eq 0) {
    $check = [char]::ConvertFromUtf32(0x2705)
    $Body += "`n**Status:** $check All $TotalTests tests passed.`n"
} else {
    $cross = [char]::ConvertFromUtf32(0x274C)
    $failWord = if ($TotalFailures -eq 1) { "test" } else { "tests" }
    $Body += "`n**Status:** $cross $TotalFailures $failWord failed.`n"
}

if ($FailuresList.Count -gt 0) {
    $Body += "`n#### Failures`n`n"

    $truncated = $false
    foreach ($failure in $FailuresList) {
        $failureBlock = "**$($failure.ClassName) > $($failure.MethodName)**`n`n"
        if ($failure.Message) {
            $failureBlock += Format-FailureMessage -message $failure.Message
        }
        $traceFence = Get-BacktickFence -text $failure.StackTrace
        $failureBlock += "<details>`n<summary>Stack Trace</summary>`n`n$traceFence`n$($failure.StackTrace)`n$traceFence`n`n</details>`n`n"

        if (($Body.Length + $failureBlock.Length) -gt 59000) {
            $truncated = $true
            break
        }
        $Body += $failureBlock
    }

    if ($truncated) {
        $artifactRef = if (-not $artifactNameFallbackApplied) {
            $delim = Get-BacktickFence -text $ArtifactName -MinLength 1
            $padded = if ($ArtifactName.StartsWith('`') -or $ArtifactName.EndsWith('`')) { " $ArtifactName " } else { $ArtifactName }
            "$delim$padded$delim"
        } else {
            $ArtifactName
        }
        $Body += "`n> [!NOTE]`n> Additional failures truncated. See full test report in the $artifactRef artifact.`n"
    }
}

Write-SummaryOutput -content $Body
Publish-PrComment -Repo $Repo -PrNumber $PrNumber -Marker $EvidenceMarker -Body $Body -TempFilePrefix 'unit-test-body'

exit 0
