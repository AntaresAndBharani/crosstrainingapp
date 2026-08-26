<#
.SYNOPSIS
    Pester v5 test suite for scripts/post-e2e-evidence.ps1.
    Verifies PR number validation, release checking, flow parsing,
    markdown body generation, sticky update/create parity, static hygiene,
    and delegation to Publish-PrComment.
#>

BeforeAll {
    Import-Module Pester -ErrorAction SilentlyContinue
    $script:PostE2EScript = Join-Path (Join-Path $PSScriptRoot "..") "post-e2e-evidence.ps1"
}

Describe 'post-e2e-evidence.ps1' {
    Context 'PR Number and CLI Guards' {
        It 'exits 1 when PrNumber is non-numeric and calls no PR comment APIs' {
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
        It 'exits 1 when release view fails on virgymia-qa and posts no comment' {
            $tempDir = Join-Path ([System.IO.Path]::GetTempPath()) "e2e-test-$([guid]::NewGuid())"
            [System.IO.Directory]::CreateDirectory($tempDir) | Out-Null
            $summaryFile = Join-Path $tempDir "summary.json"
            '[{"flow":"01_login","passed":true}]' | Set-Content -Path $summaryFile -Encoding UTF8

            $global:CommentApiCalled = $false
            Mock -CommandName gh -MockWith {
                param()
                $argsList = $args -join ' '
                if ($argsList -match 'release view') {
                    $global:LASTEXITCODE = 1
                    return 'release not found'
                }
                if ($argsList -match 'comments') {
                    $global:CommentApiCalled = $true
                }
                return ''
            }

            try {
                & $script:PostE2EScript -PrNumber "123" -SummaryPath $summaryFile -Version "v1.0.0"
                $LASTEXITCODE | Should -Be 1
                $global:CommentApiCalled | Should -BeFalse
            } finally {
                Remove-Item -Path $tempDir -Recurse -Force -ErrorAction SilentlyContinue
            }
        }
    }

    Context 'Flow parsing and delegation to Publish-PrComment' {
        It 'exits 1 when summary JSON contains 0 valid flows and posts no comment' {
            $tempDir = Join-Path ([System.IO.Path]::GetTempPath()) "e2e-test-$([guid]::NewGuid())"
            [System.IO.Directory]::CreateDirectory($tempDir) | Out-Null
            $summaryFile = Join-Path $tempDir "summary.json"
            '[]' | Set-Content -Path $summaryFile -Encoding UTF8

            $global:CommentApiCalled = $false
            Mock -CommandName gh -MockWith {
                param()
                $argsList = $args -join ' '
                if ($argsList -match 'release view') {
                    $global:LASTEXITCODE = 0
                    return 'release info'
                }
                if ($argsList -match 'comments') {
                    $global:CommentApiCalled = $true
                }
                return ''
            }

            try {
                & $script:PostE2EScript -PrNumber "123" -SummaryPath $summaryFile -Version "v1.0.0"
                $LASTEXITCODE | Should -Be 1
                $global:CommentApiCalled | Should -BeFalse
            } finally {
                Remove-Item -Path $tempDir -Recurse -Force -ErrorAction SilentlyContinue
            }
        }

        It 'delegates comment publishing to Publish-PrComment and generates expected body for all passing (POST)' {
            $tempDir = Join-Path ([System.IO.Path]::GetTempPath()) "e2e-test-$([guid]::NewGuid())"
            [System.IO.Directory]::CreateDirectory($tempDir) | Out-Null
            $summaryFile = Join-Path $tempDir "summary.json"
            @'
[
  {"flow": "01_login", "passed": true},
  {"flow": "02_workout", "passed": true}
]
'@ | Set-Content -Path $summaryFile -Encoding UTF8

            $global:CapturedBody = $null
            $global:PostCalled   = $false
            $global:PatchCalled  = $false

            Mock -CommandName Get-Command -MockWith { return [PSCustomObject]@{ Name = 'gh' } } -ParameterFilter { $Name -eq 'gh' }
            Mock -CommandName gh -MockWith {
                param()
                $global:LASTEXITCODE = 0
                $argsList = $args -join ' '

                for ($i = 0; $i -lt $args.Count; $i++) {
                    if ($args[$i] -eq '-F' -and ($i + 1) -lt $args.Count) {
                        $fArg = $args[$i + 1]
                        if ($fArg -match '^body=@(.+)$') {
                            $bodyPath = $Matches[1].Trim('"' , "'")
                            if (Test-Path -LiteralPath $bodyPath) {
                                $global:CapturedBody = Get-Content -LiteralPath $bodyPath -Raw
                            }
                        }
                    }
                }

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
                    $global:PostCalled = $true
                    return '{"id":999}'
                }
                return ''
            }

            try {
                & $script:PostE2EScript -PrNumber "123" -SummaryPath $summaryFile -Version "v1.0.0"
                $LASTEXITCODE | Should -Be 0
                $global:PostCalled | Should -BeTrue
                $global:PatchCalled | Should -BeFalse
                $global:CapturedBody | Should -Not -BeNullOrEmpty
                $global:CapturedBody | Should -Match '^<!-- e2e-evidence -->'
                $global:CapturedBody | Should -Match '01_login'
                $global:CapturedBody | Should -Match '02_workout'
                $global:CapturedBody | Should -Match 'All flows passed\.'
                $global:CapturedBody | Should -Match 'https://github\.com/AntaresAndBharani/virgymia-qa/releases/download/v1\.0\.0/report\.html'
            } finally {
                Remove-Item -Path $tempDir -Recurse -Force -ErrorAction SilentlyContinue
            }
        }

        It 'delegates comment publishing to Publish-PrComment and formats failure screenshots for failing flows (POST)' {
            $tempDir = Join-Path ([System.IO.Path]::GetTempPath()) "e2e-test-$([guid]::NewGuid())"
            [System.IO.Directory]::CreateDirectory($tempDir) | Out-Null
            $summaryFile = Join-Path $tempDir "summary.json"
            @'
[
  {"flow": "01_login", "passed": true},
  {"flow": "02_workout", "passed": false, "screenshot": "failure-02.png"}
]
'@ | Set-Content -Path $summaryFile -Encoding UTF8

            $global:CapturedBody = $null
            $global:PostCalled   = $false
            $global:PatchCalled  = $false

            Mock -CommandName Get-Command -MockWith { return [PSCustomObject]@{ Name = 'gh' } } -ParameterFilter { $Name -eq 'gh' }
            Mock -CommandName gh -MockWith {
                param()
                $global:LASTEXITCODE = 0
                $argsList = $args -join ' '

                for ($i = 0; $i -lt $args.Count; $i++) {
                    if ($args[$i] -eq '-F' -and ($i + 1) -lt $args.Count) {
                        $fArg = $args[$i + 1]
                        if ($fArg -match '^body=@(.+)$') {
                            $bodyPath = $Matches[1].Trim('"' , "'")
                            if (Test-Path -LiteralPath $bodyPath) {
                                $global:CapturedBody = Get-Content -LiteralPath $bodyPath -Raw
                            }
                        }
                    }
                }

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
                    $global:PostCalled = $true
                    return '{"id":999}'
                }
                return ''
            }

            try {
                & $script:PostE2EScript -PrNumber "123" -SummaryPath $summaryFile -Version "v1.0.0"
                $LASTEXITCODE | Should -Be 0
                $global:PostCalled | Should -BeTrue
                $global:PatchCalled | Should -BeFalse
                $global:CapturedBody | Should -Not -BeNullOrEmpty
                $global:CapturedBody | Should -Match '^<!-- e2e-evidence -->'
                $global:CapturedBody | Should -Match '01_login'
                $global:CapturedBody | Should -Match '02_workout'
                $global:CapturedBody | Should -Match 'Some flows failed\.'
                $global:CapturedBody | Should -Match 'https://github\.com/AntaresAndBharani/virgymia-qa/releases/download/v1\.0\.0/failure-02\.png'
            } finally {
                Remove-Item -Path $tempDir -Recurse -Force -ErrorAction SilentlyContinue
            }
        }

        It 'updates existing comment via PATCH when matching sticky marker is found' {
            $tempDir = Join-Path ([System.IO.Path]::GetTempPath()) "e2e-test-$([guid]::NewGuid())"
            [System.IO.Directory]::CreateDirectory($tempDir) | Out-Null
            $summaryFile = Join-Path $tempDir "summary.json"
            @'
[
  {"flow": "01_login", "passed": true}
]
'@ | Set-Content -Path $summaryFile -Encoding UTF8

            $global:CapturedBody = $null
            $global:PostCalled   = $false
            $global:PatchCalled  = $false

            Mock -CommandName Get-Command -MockWith { return [PSCustomObject]@{ Name = 'gh' } } -ParameterFilter { $Name -eq 'gh' }
            Mock -CommandName gh -MockWith {
                param()
                $global:LASTEXITCODE = 0
                $argsList = $args -join ' '

                for ($i = 0; $i -lt $args.Count; $i++) {
                    if ($args[$i] -eq '-F' -and ($i + 1) -lt $args.Count) {
                        $fArg = $args[$i + 1]
                        if ($fArg -match '^body=@(.+)$') {
                            $bodyPath = $Matches[1].Trim('"' , "'")
                            if (Test-Path -LiteralPath $bodyPath) {
                                $global:CapturedBody = Get-Content -LiteralPath $bodyPath -Raw
                            }
                        }
                    }
                }

                if ($argsList -match 'release view') {
                    return 'release exists'
                }
                if ($argsList -match 'api repos/.+/issues/123/comments' -and $argsList -notmatch 'PATCH') {
                    return '[{"id": 456, "body": "<!-- e2e-evidence -->\nOld evidence", "user": {"login": "bot-user"}}]'
                }
                if ($argsList -match 'api user') {
                    return 'bot-user'
                }
                if ($argsList -match 'PATCH') {
                    $global:PatchCalled = $true
                    return '{"id":456}'
                }
                return ''
            }

            try {
                & $script:PostE2EScript -PrNumber "123" -SummaryPath $summaryFile -Version "v1.0.0"
                $LASTEXITCODE | Should -Be 0
                $global:PatchCalled | Should -BeTrue
                $global:PostCalled | Should -BeFalse
                $global:CapturedBody | Should -Not -BeNullOrEmpty
                $global:CapturedBody | Should -Match '^<!-- e2e-evidence -->'
                $global:CapturedBody | Should -Match '01_login'
            } finally {
                Remove-Item -Path $tempDir -Recurse -Force -ErrorAction SilentlyContinue
            }
        }
    }

    Context 'Repo fallback behavior' {
        BeforeEach {
            $script:SavedRepoEnv = $env:GITHUB_REPOSITORY
        }
        AfterEach {
            $env:GITHUB_REPOSITORY = $script:SavedRepoEnv
        }

        It 'falls back to AntaresAndBharani/crosstrainingapp when -Repo is omitted and GITHUB_REPOSITORY is unset' {
            $env:GITHUB_REPOSITORY = $null
            $tempDir = Join-Path ([System.IO.Path]::GetTempPath()) "e2e-test-$([guid]::NewGuid())"
            [System.IO.Directory]::CreateDirectory($tempDir) | Out-Null
            $summaryFile = Join-Path $tempDir "summary.json"
            '[{"flow": "01_login", "passed": true}]' | Set-Content -Path $summaryFile -Encoding UTF8

            $script:TargetRepo = $null
            Mock -CommandName Get-Command -MockWith { return [PSCustomObject]@{ Name = 'gh' } } -ParameterFilter { $Name -eq 'gh' }
            Mock -CommandName gh -MockWith {
                param()
                $global:LASTEXITCODE = 0
                $argsList = $args -join ' '
                if ($argsList -match 'release view') { return 'release exists' }
                if ($argsList -match 'repos/([^/]+/[^/]+)/issues') {
                    $script:TargetRepo = $Matches[1]
                }
                if ($argsList -match 'api user') { return 'bot-user' }
                if ($argsList -match 'POST') { return '{"id":999}' }
                return '[]'
            }

            try {
                & $script:PostE2EScript -PrNumber "123" -SummaryPath $summaryFile -Version "v1.0.0"
                $LASTEXITCODE | Should -Be 0
                $script:TargetRepo | Should -Be "AntaresAndBharani/crosstrainingapp"
            } finally {
                Remove-Item -Path $tempDir -Recurse -Force -ErrorAction SilentlyContinue
            }
        }

        It 'falls back to GITHUB_REPOSITORY when -Repo is omitted or blank' {
            $env:GITHUB_REPOSITORY = "CustomOrg/e2e-repo"
            $tempDir = Join-Path ([System.IO.Path]::GetTempPath()) "e2e-test-$([guid]::NewGuid())"
            [System.IO.Directory]::CreateDirectory($tempDir) | Out-Null
            $summaryFile = Join-Path $tempDir "summary.json"
            '[{"flow": "01_login", "passed": true}]' | Set-Content -Path $summaryFile -Encoding UTF8

            $script:TargetRepo = $null
            Mock -CommandName Get-Command -MockWith { return [PSCustomObject]@{ Name = 'gh' } } -ParameterFilter { $Name -eq 'gh' }
            Mock -CommandName gh -MockWith {
                param()
                $global:LASTEXITCODE = 0
                $argsList = $args -join ' '
                if ($argsList -match 'release view') { return 'release exists' }
                if ($argsList -match 'repos/([^/]+/[^/]+)/issues') {
                    $script:TargetRepo = $Matches[1]
                }
                if ($argsList -match 'api user') { return 'bot-user' }
                if ($argsList -match 'POST') { return '{"id":999}' }
                return '[]'
            }

            try {
                & $script:PostE2EScript -PrNumber "123" -SummaryPath $summaryFile -Version "v1.0.0"
                $LASTEXITCODE | Should -Be 0
                $script:TargetRepo | Should -Be "CustomOrg/e2e-repo"
            } finally {
                Remove-Item -Path $tempDir -Recurse -Force -ErrorAction SilentlyContinue
            }
        }

        It 'passes explicit -Repo through to gh api' {
            $env:GITHUB_REPOSITORY = "EnvOrg/env-repo"
            $tempDir = Join-Path ([System.IO.Path]::GetTempPath()) "e2e-test-$([guid]::NewGuid())"
            [System.IO.Directory]::CreateDirectory($tempDir) | Out-Null
            $summaryFile = Join-Path $tempDir "summary.json"
            '[{"flow": "01_login", "passed": true}]' | Set-Content -Path $summaryFile -Encoding UTF8

            $script:TargetRepo = $null
            Mock -CommandName Get-Command -MockWith { return [PSCustomObject]@{ Name = 'gh' } } -ParameterFilter { $Name -eq 'gh' }
            Mock -CommandName gh -MockWith {
                param()
                $global:LASTEXITCODE = 0
                $argsList = $args -join ' '
                if ($argsList -match 'release view') { return 'release exists' }
                if ($argsList -match 'repos/([^/]+/[^/]+)/issues') {
                    $script:TargetRepo = $Matches[1]
                }
                if ($argsList -match 'api user') { return 'bot-user' }
                if ($argsList -match 'POST') { return '{"id":999}' }
                return '[]'
            }

            try {
                & $script:PostE2EScript -Repo "ExplicitOrg/custom-repo" -PrNumber "123" -SummaryPath $summaryFile -Version "v1.0.0"
                $LASTEXITCODE | Should -Be 0
                $script:TargetRepo | Should -Be "ExplicitOrg/custom-repo"
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
