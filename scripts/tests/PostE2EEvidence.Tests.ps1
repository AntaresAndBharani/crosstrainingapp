<#
.SYNOPSIS
    Pester v5 test suite for scripts/post-e2e-evidence.ps1.
    Verifies PR number validation, release checking, flow parsing,
    markdown body generation, sticky update/create parity, static hygiene,
    and delegation to Publish-PrComment.
#>

BeforeAll {
    Import-Module Pester -ErrorAction SilentlyContinue
    . (Join-Path (Join-Path $PSScriptRoot "..") "lib/PrComment.ps1")
    $script:PostE2EScript = Join-Path (Join-Path $PSScriptRoot "..") "post-e2e-evidence.ps1"
}

Describe 'post-e2e-evidence.ps1' {
    Context 'PR Number and CLI Guards' {
        It 'exits 1 when PrNumber is non-numeric and calls no PR comment APIs' {
            Mock -CommandName gh -MockWith { throw "gh should not be called" }

            & $script:PostE2EScript -PrNumber "abc" -SummaryPath "dummy.json"
            $LASTEXITCODE | Should -Be 1
            Should -Invoke -CommandName gh -Times 0 -Exactly
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
            Should -Invoke -CommandName gh -Times 0 -Exactly -ParameterFilter { ($args -join ' ') -match 'comments' }
        }

        It 'exits 1 when SummaryPath is missing or non-existent' {
            Mock -CommandName gh -MockWith { throw "gh should not be called" }

            & $script:PostE2EScript -PrNumber "123" -SummaryPath "nonexistent-summary-$([guid]::NewGuid()).json"
            $LASTEXITCODE | Should -Be 1
            Should -Invoke -CommandName gh -Times 0 -Exactly
        }
    }

    Context 'Release verification guard' {
        It 'exits 1 when release view fails on virgymia-qa and posts no comment' {
            $tempDir = Join-Path ([System.IO.Path]::GetTempPath()) "e2e-test-$([guid]::NewGuid())"
            [System.IO.Directory]::CreateDirectory($tempDir) | Out-Null
            $summaryFile = Join-Path $tempDir "summary.json"
            '[{"flow":"01_login","passed":true}]' | Set-Content -Path $summaryFile -Encoding UTF8

            Mock -CommandName gh -MockWith {
                param()
                $argsList = $args -join ' '
                if ($argsList -match 'release view') {
                    $global:LASTEXITCODE = 1
                    return 'release not found'
                }
                return ''
            }

            try {
                & $script:PostE2EScript -PrNumber "123" -SummaryPath $summaryFile -Version "v1.0.0"
                $LASTEXITCODE | Should -Be 1
                Should -Invoke -CommandName gh -Times 0 -Exactly -ParameterFilter { ($args -join ' ') -match 'comments' }
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

            Mock -CommandName gh -MockWith {
                param()
                $argsList = $args -join ' '
                if ($argsList -match 'release view') {
                    $global:LASTEXITCODE = 0
                    return 'release info'
                }
                return ''
            }

            try {
                & $script:PostE2EScript -PrNumber "123" -SummaryPath $summaryFile -Version "v1.0.0"
                $LASTEXITCODE | Should -Be 1
                Should -Invoke -CommandName gh -Times 0 -Exactly -ParameterFilter { ($args -join ' ') -match 'comments' }
            } finally {
                Remove-Item -Path $tempDir -Recurse -Force -ErrorAction SilentlyContinue
            }
        }

        It 'invokes Publish-PrComment directly with expected marker and non-empty body' {
            $tempDir = Join-Path ([System.IO.Path]::GetTempPath()) "e2e-test-$([guid]::NewGuid())"
            [System.IO.Directory]::CreateDirectory($tempDir) | Out-Null
            $summaryFile = Join-Path $tempDir "summary.json"
            @'
[
  {"flow": "01_login", "passed": true}
]
'@ | Set-Content -Path $summaryFile -Encoding UTF8

            Mock -CommandName Publish-PrComment -MockWith { return $true }
            Mock -CommandName gh -MockWith {
                param()
                $argsList = $args -join ' '
                if ($argsList -match 'release view') {
                    $global:LASTEXITCODE = 0
                    return 'release exists'
                }
                return ''
            }

            try {
                & $script:PostE2EScript -PrNumber "123" -SummaryPath $summaryFile -Version "v1.0.0"
                $LASTEXITCODE | Should -Be 0
                Should -Invoke -CommandName Publish-PrComment -Times 1 -Exactly -ParameterFilter {
                    $Marker -eq '<!-- e2e-evidence -->' -and -not [string]::IsNullOrWhiteSpace($Body)
                }
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
                Should -Invoke -CommandName gh -Times 1 -Exactly -ParameterFilter {
                    ($args -join ' ') -match 'PATCH' -and ($args -join ' ') -match 'issues/comments/456'
                }
                Should -Invoke -CommandName gh -Times 0 -Exactly -ParameterFilter {
                    ($args -join ' ') -match 'POST'
                }
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
            $global:TargetRepo = $null
        }
        AfterEach {
            $env:GITHUB_REPOSITORY = $script:SavedRepoEnv
            $global:TargetRepo = $null
        }

        It 'falls back to AntaresAndBharani/crosstrainingapp when -Repo is omitted and GITHUB_REPOSITORY is unset' {
            $env:GITHUB_REPOSITORY = $null
            $tempDir = Join-Path ([System.IO.Path]::GetTempPath()) "e2e-test-$([guid]::NewGuid())"
            [System.IO.Directory]::CreateDirectory($tempDir) | Out-Null
            $summaryFile = Join-Path $tempDir "summary.json"
            '[{"flow": "01_login", "passed": true}]' | Set-Content -Path $summaryFile -Encoding UTF8

            $global:TargetRepo = $null
            Mock -CommandName Get-Command -MockWith { return [PSCustomObject]@{ Name = 'gh' } } -ParameterFilter { $Name -eq 'gh' }
            Mock -CommandName gh -MockWith {
                param()
                $global:LASTEXITCODE = 0
                $argsList = $args -join ' '
                if ($argsList -match 'release view') { return 'release exists' }
                if ($argsList -match 'repos/([^/]+/[^/]+)/issues') {
                    $global:TargetRepo = $Matches[1]
                }
                if ($argsList -match 'api user') { return 'bot-user' }
                if ($argsList -match 'POST') { return '{"id":999}' }
                return '[]'
            }

            try {
                & $script:PostE2EScript -PrNumber "123" -SummaryPath $summaryFile -Version "v1.0.0"
                $LASTEXITCODE | Should -Be 0
                $global:TargetRepo | Should -Be "AntaresAndBharani/crosstrainingapp"
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

            $global:TargetRepo = $null
            Mock -CommandName Get-Command -MockWith { return [PSCustomObject]@{ Name = 'gh' } } -ParameterFilter { $Name -eq 'gh' }
            Mock -CommandName gh -MockWith {
                param()
                $global:LASTEXITCODE = 0
                $argsList = $args -join ' '
                if ($argsList -match 'release view') { return 'release exists' }
                if ($argsList -match 'repos/([^/]+/[^/]+)/issues') {
                    $global:TargetRepo = $Matches[1]
                }
                if ($argsList -match 'api user') { return 'bot-user' }
                if ($argsList -match 'POST') { return '{"id":999}' }
                return '[]'
            }

            try {
                & $script:PostE2EScript -PrNumber "123" -SummaryPath $summaryFile -Version "v1.0.0"
                $LASTEXITCODE | Should -Be 0
                $global:TargetRepo | Should -Be "CustomOrg/e2e-repo"
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

            $global:TargetRepo = $null
            Mock -CommandName Get-Command -MockWith { return [PSCustomObject]@{ Name = 'gh' } } -ParameterFilter { $Name -eq 'gh' }
            Mock -CommandName gh -MockWith {
                param()
                $global:LASTEXITCODE = 0
                $argsList = $args -join ' '
                if ($argsList -match 'release view') { return 'release exists' }
                if ($argsList -match 'repos/([^/]+/[^/]+)/issues') {
                    $global:TargetRepo = $Matches[1]
                }
                if ($argsList -match 'api user') { return 'bot-user' }
                if ($argsList -match 'POST') { return '{"id":999}' }
                return '[]'
            }

            try {
                & $script:PostE2EScript -Repo "ExplicitOrg/custom-repo" -PrNumber "123" -SummaryPath $summaryFile -Version "v1.0.0"
                $LASTEXITCODE | Should -Be 0
                $global:TargetRepo | Should -Be "ExplicitOrg/custom-repo"
            } finally {
                Remove-Item -Path $tempDir -Recurse -Force -ErrorAction SilentlyContinue
            }
        }
    }

    Context 'Static hygiene' {
        BeforeAll {
            function script:Find-RawGhApiCommentCalls {
                param (
                    [System.Management.Automation.Language.Ast]$Ast
                )
                $assignments = @($Ast.FindAll({ $args[0] -is [System.Management.Automation.Language.AssignmentStatementAst] }, $true))
                $commands = $Ast.FindAll({ $args[0] -is [System.Management.Automation.Language.CommandAst] }, $true)
                $violations = @($commands | Where-Object {
                    $cmd = $_
                    $cmdName = $cmd.GetCommandName()
                    $isGh = $cmdName -in @('gh', 'gh.exe')

                    if (-not $isGh -and $cmd.CommandElements.Count -gt 0 -and $cmd.CommandElements[0] -is [System.Management.Automation.Language.VariableExpressionAst]) {
                        $varName = $cmd.CommandElements[0].VariablePath.UserPath
                        $matchingAssignments = @($assignments | Where-Object {
                            $assign = $_
                            if ($assign.Extent.EndOffset -gt $cmd.Extent.StartOffset) { return $false }
                            $leftVar = $null
                            if ($assign.Left -is [System.Management.Automation.Language.VariableExpressionAst]) {
                                $leftVar = $assign.Left.VariablePath.UserPath
                            } elseif ($assign.Left -is [System.Management.Automation.Language.ConvertExpressionAst] -and $assign.Left.Child -is [System.Management.Automation.Language.VariableExpressionAst]) {
                                $leftVar = $assign.Left.Child.VariablePath.UserPath
                            }
                            return ($leftVar -eq $varName)
                        })
                        if ($matchingAssignments.Count -gt 0) {
                            $lastAssign = $matchingAssignments[-1]
                            $val = $null
                            if ($lastAssign.Right -is [System.Management.Automation.Language.CommandExpressionAst] -and $lastAssign.Right.Expression -is [System.Management.Automation.Language.StringConstantExpressionAst]) {
                                $val = $lastAssign.Right.Expression.Value
                            } elseif ($lastAssign.Right -is [System.Management.Automation.Language.StringConstantExpressionAst]) {
                                $val = $lastAssign.Right.Value
                            } else {
                                $val = $lastAssign.Right.Extent.Text.Trim("'`"")
                            }
                            if ($val -in @('gh', 'gh.exe')) {
                                $isGh = $true
                            }
                        }
                    }

                    if (-not $isGh) { return $false }

                    $elements = @($cmd.CommandElements | Select-Object -Skip 1)
                    $hasApi = $false
                    foreach ($elem in $elements) {
                        if (($elem -is [System.Management.Automation.Language.StringConstantExpressionAst] -and $elem.Value -eq 'api') -or
                            ($elem.Extent.Text.Trim("'`"") -eq 'api')) {
                            $hasApi = $true
                            break
                        }
                    }
                    if (-not $hasApi) { return $false }
                    foreach ($elem in $elements) {
                        if ($elem.Extent.Text -match 'issues/.+/comments|issues/comments') {
                            return $true
                        }
                    }
                    return $false
                })
                return ,$violations
            }
        }

        It 'contains no raw gh api comment listing, posting, or patching calls via AST' {
            $tokens = $null
            $errors = $null
            $ast = [System.Management.Automation.Language.Parser]::ParseFile($script:PostE2EScript, [ref]$tokens, [ref]$errors)
            $errors.Count | Should -Be 0
            $violations = Find-RawGhApiCommentCalls -Ast $ast
            $violations.Count | Should -Be 0
        }

        It 'detects raw gh api comment calls across formatting variants that regex checks miss' {
            $oldRegex1 = 'gh api repos/.+/issues/\$PrNumber/comments'
            $oldRegex2 = 'gh api repos/.+/issues/comments'

            # A formatting variant that bypassed the old regex check
            $evasiveSnippet = 'gh api "repos/$Repo/issues/$($Pr)/comments"'
            $evasiveSnippet | Should -Not -Match $oldRegex1
            $evasiveSnippet | Should -Not -Match $oldRegex2

            $variantSnippets = @(
                'gh api "repos/$Repo/issues/$($Pr)/comments"',
                'gh api "repos/${Repo}/issues/${PrNumber}/comments"',
                'gh api ("repos/" + $Repo + "/issues/" + $Pr + "/comments")',
                'gh api repos/foo/issues/comments/456 -X PATCH',
                'gh api "issues/$PrNumber/comments"',
                '$cmd = ''gh''; & $cmd api "repos/$Repo/issues/$Pr/comments"',
                '$cmd = ''gh.exe''; & $cmd api repos/$Repo/issues/$Pr/comments'
            )

            foreach ($code in $variantSnippets) {
                $tokens = $null
                $errors = $null
                $ast = [System.Management.Automation.Language.Parser]::ParseInput($code, [ref]$tokens, [ref]$errors)
                $violations = Find-RawGhApiCommentCalls -Ast $ast
                $violations.Count | Should -BeGreaterThan 0
            }
        }

        It 'dot-sources lib/PrComment.ps1' {
            $content = Get-Content -LiteralPath $script:PostE2EScript -Raw
            $content | Should -Match "lib/PrComment\.ps1"
        }
    }
}
