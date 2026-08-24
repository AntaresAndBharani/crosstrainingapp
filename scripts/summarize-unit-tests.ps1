param (
    [string]$ResultsDir = "app/build/test-results/testDebugUnitTest",
    [string]$OutFile = "unit-test-summary.md"
)

$ErrorActionPreference = "Stop"

function Sanitize-Paths ([string]$text) {
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

function Format-StackTrace ([string]$trace) {
    if (-not $trace) { return "" }
    
    $trace = Sanitize-Paths -text $trace
    
    # Cap trace at 40 lines
    $lines = $trace -split '\r?\n'
    if ($lines.Count -gt 40) {
        $truncatedLines = $lines[0..39]
        $trace = ($truncatedLines -join "`n") + "`n... (truncated)"
    } else {
        $trace = $lines -join "`n"
    }
    
    return $trace.Trim()
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
    $NoResultsBody = "<!-- unit-test-evidence -->`n### :test_tube: Unit Test Results`n`nNo unit test results found.`n"
    Write-SummaryOutput -content $NoResultsBody
    exit 0
}

$TotalTests = 0
$TotalFailures = 0
$TotalSkipped = 0
$TotalTime = 0.0
$FailuresList = [System.Collections.Generic.List[PSObject]]::new()
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
                    $message = Sanitize-Paths -text $rawMessage
                    $rawTrace = $failNode.InnerText
                    $formattedTrace = Format-StackTrace -trace $rawTrace

                    $FailuresList.Add([PSObject]@{
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
    $NoResultsBody = "<!-- unit-test-evidence -->`n### :test_tube: Unit Test Results`n`nNo unit test results found.`n"
    Write-SummaryOutput -content $NoResultsBody
    exit 0
}

$TotalPassed = $TotalTests - $TotalFailures - $TotalSkipped
if ($TotalPassed -lt 0) { $TotalPassed = 0 }

$ElapsedFormatted = "$($TotalTime.ToString("0.00", [System.Globalization.CultureInfo]::InvariantCulture))s"

$Body = "<!-- unit-test-evidence -->`n### :test_tube: Unit Test Results`n`n"
$Body += "| Total | Passed | Failed | Skipped | Elapsed |`n"
$Body += "|---|---|---|---|---|`n"
$Body += "| $TotalTests | $TotalPassed | $TotalFailures | $TotalSkipped | $ElapsedFormatted |`n"

if ($FailuresList.Count -gt 0) {
    $Body += "`n#### Failures`n`n"
    
    $truncated = $false
    foreach ($failure in $FailuresList) {
        $failureBlock = "**$($failure.ClassName) > $($failure.MethodName)**`n`n"
        if ($failure.Message) {
            $msg = $failure.Message
            if ($msg -match '\r?\n') {
                $failureBlock += "**Message:**`n```````n$msg`n```````n`n"
            } elseif ($msg -match '`') {
                $failureBlock += "**Message:** ```` $msg ````" + "`n`n"
            } else {
                $failureBlock += "**Message:** ``$msg```n`n"
            }
        }
        $failureBlock += "<details>`n<summary>Stack Trace</summary>`n`n```````n$($failure.StackTrace)`n```````n`n</details>`n`n"
        
        if (($Body.Length + $failureBlock.Length) -gt 59000) {
            $truncated = $true
            break
        }
        $Body += $failureBlock
    }

    if ($truncated) {
        $Body += "`n> [!NOTE]`n> Additional failures truncated. See full test report in the unit test report artifact.`n"
    }
}

Write-SummaryOutput -content $Body

exit 0
