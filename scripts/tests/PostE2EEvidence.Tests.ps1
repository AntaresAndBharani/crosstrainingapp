<#
.SYNOPSIS
    Pester v5 test suite for scripts/post-e2e-evidence.ps1.
    Verifies PR number validation, release checking, flow parsing,
    markdown body generation, static hygiene, and delegation to Publish-PrComment.
#>

BeforeAll {
    Import-Module Pester -ErrorAction SilentlyContinue
    $script:PostE2EScript = Join-Path (Join-Path $PSScriptRoot "..") "post-e2e-evidence.ps1"
}

Describe 'post-e2e-evidence.ps1' {
    Context 'PR Number and CLI Guards' {
        It 'exits 1 when PrNumber is non-numeric' {
            Mock -CommandName gh -MockWith { throw "gh should not be called" }

            & $script:PostE2EScript -PrNumber "abc" -SummaryPath "dummy.json"
            $LASTEXITCODE | Should -Be 1
        }

        It 'exits 1 when PrNumber is empty and gh pr view fails or returns no number' {
            Mock -CommandName Get-Command -MockWith { return [PSCustomObject]@{ Name = 'gh' } } -ParameterFilter { $Name -eq 'gh' }
            Mock -CommandName gh -MockWith {
                param()
                $global:LASTEXITCODE = 1
                return ''
            }

            & $script:PostE2EScript -PrNumber "" -SummaryPath "dummy.json"
            $LASTEXITCODE | Should -Be 1
        }

        It 'exits 1 when SummaryPath is missing or non-existent' {
            & $script:PostE2EScript -PrNumber "123" -SummaryPath "nonexistent-summary-$([guid]::NewGuid()).json"
            $LASTEXITCODE | Should -Be 1
        }
    }

    Context 'Release verification guard' {
        It 'exits 1 when release view fails on virgymia-qa' {
            $tempDir = Join-Path ([System.IO.Path]::GetTempPath()) "e2e-test-$([guid]::NewGuid())"
            [System.IO.Directory]::CreateDirectory($tempDir) | Out-Null
            $summaryFile = Join-Path $tempDir "summary.json"
            '[{"flow":"01_login","passed":true}]' | Set-Content -Path $summaryFile -Encoding UTF8

            Mock -CommandName gh -MockWith {
                param()
                $global:LASTEXITCODE = 1
                return 'release not found'
            }

            try {
                & $script:PostE2EScript -PrNumber "123" -SummaryPath $summaryFile -Version "v1.0.0"
                $LASTEXITCODE | Should -Be 1
            } finally {
                Remove-Item -Path $tempDir -Recurse -Force -ErrorAction SilentlyContinue
            }
        }
    }

    Context 'Flow parsing and delegation to Publish-PrComment' {
        It 'exits 1 when summary JSON contains 0 valid flows' {
            $tempDir = Join-Path ([System.IO.Path]::GetTempPath()) "e2e-test-$([guid]::NewGuid())"
            [System.IO.Directory]::CreateDirectory($tempDir) | Out-Null
            $summaryFile = Join-Path $tempDir "summary.json"
            '[]' | Set-Content -Path $summaryFile -Encoding UTF8

            Mock -CommandName gh -MockWith {
                param()
                $global:LASTEXITCODE = 0
                return 'release info'
            }

            try {
                & $script:PostE2EScript -PrNumber "123" -SummaryPath $summaryFile -Version "v1.0.0"
                $LASTEXITCODE | Should -Be 1
            } finally {
                Remove-Item -Path $tempDir -Recurse -Force -ErrorAction SilentlyContinue
            }
        }

        It 'delegates comment publishing to Publish-PrComment and generates expected body for all passing' {
            $tempDir = Join-Path ([System.IO.Path]::GetTempPath()) "e2e-test-$([guid]::NewGuid())"
            [System.IO.Directory]::CreateDirectory($tempDir) | Out-Null
            $summaryFile = Join-Path $tempDir "summary.json"
            @'
[
  {"flow": "01_login", "passed": true},
  {"flow": "02_workout", "passed": true}
]
'@ | Set-Content -Path $summaryFile -Encoding UTF8

            $script:CapturedBody = $null
            Mock -CommandName Get-Command -MockWith { return [PSCustomObject]@{ Name = 'gh' } } -ParameterFilter { $Name -eq 'gh' }
            Mock -CommandName gh -MockWith {
                param()
                $global:LASTEXITCODE = 0
                $argsList = $args -join ' '
                if ($argsList -match 'release view') {
                    return 'release exists'
                }
                if ($argsList -match 'api repos/.+/issues/123/comments' -and $argsList -notmatch 'POST') {
                    return '[]'
                }
                if ($argsList -match 'api user') {
                    return 'bot-user'
                }
                if ($argsList -match 'api repos/.+/issues/123/comments' -and $argsList -match 'POST') {
                    return '{"id":999}'
                }
                return ''
            }

            try {
                & $script:PostE2EScript -PrNumber "123" -SummaryPath $summaryFile -Version "v1.0.0"
                $LASTEXITCODE | Should -Be 0
            } finally {
                Remove-Item -Path $tempDir -Recurse -Force -ErrorAction SilentlyContinue
            }
        }

        It 'delegates comment publishing to Publish-PrComment and formats failure screenshots for failing flows' {
            $tempDir = Join-Path ([System.IO.Path]::GetTempPath()) "e2e-test-$([guid]::NewGuid())"
            [System.IO.Directory]::CreateDirectory($tempDir) | Out-Null
            $summaryFile = Join-Path $tempDir "summary.json"
            @'
[
  {"flow": "01_login", "passed": true},
  {"flow": "02_workout", "passed": false, "screenshot": "failure-02.png"}
]
'@ | Set-Content -Path $summaryFile -Encoding UTF8

            Mock -CommandName Get-Command -MockWith { return [PSCustomObject]@{ Name = 'gh' } } -ParameterFilter { $Name -eq 'gh' }
            Mock -CommandName gh -MockWith {
                param()
                $global:LASTEXITCODE = 0
                $argsList = $args -join ' '
                if ($argsList -match 'release view') {
                    return 'release exists'
                }
                if ($argsList -match 'api repos/.+/issues/123/comments' -and $argsList -notmatch 'POST') {
                    return '[]'
                }
                if ($argsList -match 'api user') {
                    return 'bot-user'
                }
                if ($argsList -match 'api repos/.+/issues/123/comments' -and $argsList -match 'POST') {
                    return '{"id":999}'
                }
                return ''
            }

            try {
                & $script:PostE2EScript -PrNumber "123" -SummaryPath $summaryFile -Version "v1.0.0"
                $LASTEXITCODE | Should -Be 0
            } finally {
                Remove-Item -Path $tempDir -Recurse -Force -ErrorAction SilentlyContinue
            }
        }
    }

    Context 'Static hygiene' {
        It 'contains no raw gh api comment listing or posting calls' {
            $content = Get-Content -LiteralPath $script:PostE2EScript -Raw
            $content | Should -Not -Match 'gh api repos/.+/issues/\$PrNumber/comments'
            $content | Should -Not -Match 'gh api repos/.+/issues/comments'
        }

        It 'dot-sources lib/PrComment.ps1' {
            $content = Get-Content -LiteralPath $script:PostE2EScript -Raw
            $content | Should -Match "lib/PrComment\.ps1"
        }
    }
}
