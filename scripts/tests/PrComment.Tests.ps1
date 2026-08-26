<#
.SYNOPSIS
    Pester v5 test suite for scripts/lib/PrComment.ps1 (Publish-PrComment).
    Proves guard clauses, pagination, sticky comment replacement/creation,
    regex-literal marker matching, error resilience, and temp file cleanup.
#>

BeforeAll {
    Import-Module Pester -ErrorAction SilentlyContinue
    . (Join-Path (Join-Path $PSScriptRoot "..") "lib\PrComment.ps1")

    function Set-GhAvailable {
        param([bool]$Available = $true)
        if ($Available) {
            Mock -CommandName Get-Command -MockWith { return [PSCustomObject]@{ Name = 'gh' } } -ParameterFilter { $Name -eq 'gh' }
        } else {
            Mock -CommandName Get-Command -MockWith { return $null } -ParameterFilter { $Name -eq 'gh' }
        }
    }

    function New-GhApiMock {
        [CmdletBinding()]
        param(
            [Parameter()]
            $Comments = '[]',

            [string]$CurrentUser = 'bot-user',

            [scriptblock]$OnPost = $null,

            [scriptblock]$OnPatch = $null,

            [scriptblock]$OnList = $null,

            [int]$ExitCode = 0,

            [string]$ThrowException = $null
        )

        $commentsString = if ($Comments -is [string]) {
            $Comments
        } elseif ($null -ne $Comments) {
            $Comments | ConvertTo-Json -Compress
        } else {
            '[]'
        }

        $mockScript = {
            param()
            if ($ThrowException) {
                throw $ThrowException
            }

            $exitCodeVal = if ($null -ne $ExitCode) { $ExitCode } else { 0 }
            $global:LASTEXITCODE = $exitCodeVal
            if ($exitCodeVal -ne 0) {
                return ''
            }

            $argsList = $args -join ' '

            if ($argsList -match 'api repos/.+/issues/\d+/comments' -and $argsList -notmatch 'POST' -and $argsList -notmatch 'PATCH') {
                if ($OnList) {
                    & $OnList $argsList $args
                }
                return $commentsString
            }

            if ($argsList -match 'api user') {
                return $CurrentUser
            }

            if ($argsList -match 'PATCH') {
                $commentId = if ($argsList -match 'comments/(\d+)') { [int]$Matches[1] } else { $null }
                if ($OnPatch) {
                    & $OnPatch $commentId $argsList $args
                }
                $retId = if ($null -ne $commentId) { $commentId } else { 1 }
                return "{`"id`":$retId}"
            }

            if ($argsList -match 'POST') {
                if ($OnPost) {
                    & $OnPost $argsList $args
                }
                return '{"id":999}'
            }

            return ''
        }.GetNewClosure()

        Mock -CommandName gh -MockWith $mockScript
    }
}

Describe 'Publish-PrComment' {
    Context 'Guard and validation' {
        It 'returns $false and emits warning when gh CLI is missing' {
            Set-GhAvailable -Available $false
            Mock -CommandName gh -MockWith { throw "gh should not be called" }

            $result = Publish-PrComment -PrNumber "123" -Marker "<!-- marker -->" -Body "Test"
            $result | Should -BeFalse
            Should -Invoke -CommandName gh -Times 0 -Exactly
        }

        It 'returns $false when PrNumber is blank or whitespace' {
            Mock -CommandName gh -MockWith { throw "gh should not be called" }

            $r1 = Publish-PrComment -PrNumber "" -Marker "<!-- marker -->" -Body "Test"
            $r2 = Publish-PrComment -PrNumber "   " -Marker "<!-- marker -->" -Body "Test"
            $r3 = Publish-PrComment -PrNumber $null -Marker "<!-- marker -->" -Body "Test"

            $r1 | Should -BeFalse
            $r2 | Should -BeFalse
            $r3 | Should -BeFalse
            Should -Invoke -CommandName gh -Times 0 -Exactly
        }

        It 'returns $false when PrNumber is non-numeric' {
            Mock -CommandName gh -MockWith { throw "gh should not be called" }

            $r1 = Publish-PrComment -PrNumber "abc" -Marker "<!-- marker -->" -Body "Test"
            $r2 = Publish-PrComment -PrNumber "12a" -Marker "<!-- marker -->" -Body "Test"
            $r3 = Publish-PrComment -PrNumber "12 34" -Marker "<!-- marker -->" -Body "Test"
            $r4 = Publish-PrComment -PrNumber "-5" -Marker "<!-- marker -->" -Body "Test"

            $r1 | Should -BeFalse
            $r2 | Should -BeFalse
            $r3 | Should -BeFalse
            $r4 | Should -BeFalse
            Should -Invoke -CommandName gh -Times 0 -Exactly
        }
    }

    Context 'Repo parameter normalization and fallback' {
        BeforeEach {
            $script:SavedRepoEnv = $env:GITHUB_REPOSITORY
        }
        AfterEach {
            $env:GITHUB_REPOSITORY = $script:SavedRepoEnv
        }

        It 'falls back to AntaresAndBharani/crosstrainingapp when -Repo is omitted and GITHUB_REPOSITORY is unset' {
            $env:GITHUB_REPOSITORY = $null
            $script:TargetRepo = $null

            Set-GhAvailable -Available $true
            New-GhApiMock -OnList {
                param($argsList)
                if ($argsList -match 'repos/([^/]+/[^/]+)/issues') {
                    $script:TargetRepo = $Matches[1]
                }
            }

            $result = Publish-PrComment -PrNumber "123" -Marker "<!-- marker -->" -Body "Test"
            $result | Should -BeTrue
            $script:TargetRepo | Should -Be "AntaresAndBharani/crosstrainingapp"
        }

        It 'falls back to AntaresAndBharani/crosstrainingapp when -Repo is empty or whitespace and GITHUB_REPOSITORY is unset' {
            $env:GITHUB_REPOSITORY = $null
            $script:TargetRepo = $null

            Set-GhAvailable -Available $true
            New-GhApiMock -OnList {
                param($argsList)
                if ($argsList -match 'repos/([^/]+/[^/]+)/issues') {
                    $script:TargetRepo = $Matches[1]
                }
            }

            $r1 = Publish-PrComment -Repo "" -PrNumber "123" -Marker "<!-- marker -->" -Body "Test"
            $script:TargetRepo | Should -Be "AntaresAndBharani/crosstrainingapp"

            $script:TargetRepo = $null
            $r2 = Publish-PrComment -Repo "   " -PrNumber "123" -Marker "<!-- marker -->" -Body "Test"
            $script:TargetRepo | Should -Be "AntaresAndBharani/crosstrainingapp"

            $r1 | Should -BeTrue
            $r2 | Should -BeTrue
        }

        It 'falls back to GITHUB_REPOSITORY when -Repo is omitted, empty, or whitespace' {
            $env:GITHUB_REPOSITORY = "CustomOrg/custom-repo"
            $script:TargetRepo = $null

            Set-GhAvailable -Available $true
            New-GhApiMock -OnList {
                param($argsList)
                if ($argsList -match 'repos/([^/]+/[^/]+)/issues') {
                    $script:TargetRepo = $Matches[1]
                }
            }

            $r1 = Publish-PrComment -PrNumber "123" -Marker "<!-- marker -->" -Body "Test"
            $script:TargetRepo | Should -Be "CustomOrg/custom-repo"

            $script:TargetRepo = $null
            $r2 = Publish-PrComment -Repo "" -PrNumber "123" -Marker "<!-- marker -->" -Body "Test"
            $script:TargetRepo | Should -Be "CustomOrg/custom-repo"

            $script:TargetRepo = $null
            $r3 = Publish-PrComment -Repo "  " -PrNumber "123" -Marker "<!-- marker -->" -Body "Test"
            $script:TargetRepo | Should -Be "CustomOrg/custom-repo"

            $r1 | Should -BeTrue
            $r2 | Should -BeTrue
            $r3 | Should -BeTrue
        }

        It 'uses explicit -Repo when provided, taking precedence over GITHUB_REPOSITORY' {
            $env:GITHUB_REPOSITORY = "EnvOrg/env-repo"
            $script:TargetRepo = $null

            Set-GhAvailable -Available $true
            New-GhApiMock -OnList {
                param($argsList)
                if ($argsList -match 'repos/([^/]+/[^/]+)/issues') {
                    $script:TargetRepo = $Matches[1]
                }
            }

            $result = Publish-PrComment -Repo "ExplicitOrg/explicit-repo" -PrNumber "123" -Marker "<!-- marker -->" -Body "Test"
            $result | Should -BeTrue
            $script:TargetRepo | Should -Be "ExplicitOrg/explicit-repo"
        }
    }

    Context 'Listing and Pagination' {
        It 'includes --paginate and --slurp when querying comments' {
            $script:PaginateCalled = $false
            $script:SlurpCalled = $false

            Set-GhAvailable -Available $true
            New-GhApiMock -OnList {
                param($argsList)
                if ($argsList -match '--paginate') {
                    $script:PaginateCalled = $true
                }
                if ($argsList -match '--slurp') {
                    $script:SlurpCalled = $true
                }
            }

            $result = Publish-PrComment -Repo "test/repo" -PrNumber "123" -Marker "<!-- marker -->" -Body "Test"
            $result | Should -BeTrue
            $script:PaginateCalled | Should -BeTrue
            $script:SlurpCalled | Should -BeTrue
        }

        It 'discovers and PATCHes marker comment located on the second page of multi-page response' {
            $multiPageRaw = '[
                [
                    {"id": 101, "body": "Page 1 comment", "user": {"login": "bot-user"}},
                    {"id": 102, "body": "Another page 1 comment", "user": {"login": "bot-user"}}
                ],
                [
                    {"id": 201, "body": "Page 2 first comment", "user": {"login": "bot-user"}},
                    {"id": 202, "body": "<!-- marker -->`nPage 2 evidence", "user": {"login": "bot-user"}}
                ]
            ]'

            $script:PatchedCommentId = $null
            $script:Posted = $false

            Set-GhAvailable -Available $true
            New-GhApiMock -Comments $multiPageRaw -OnPatch {
                param($commentId)
                $script:PatchedCommentId = $commentId
            } -OnPost {
                $script:Posted = $true
            }

            $result = Publish-PrComment -Repo "test/repo" -PrNumber "123" -Marker "<!-- marker -->" -Body "Updated evidence"
            $result | Should -BeTrue
            $script:PatchedCommentId | Should -Be 202
            $script:Posted | Should -BeFalse
        }
    }

    Context 'Sticky comment replacement vs creation' {
        It 'PATCHes existing comment when marker matches' {
            $comments = @(
                @{ id = 101; body = "Unrelated comment"; user = @{ login = "bot-user" } },
                @{ id = 102; body = "<!-- marker -->`nExisting evidence"; user = @{ login = "bot-user" } }
            )

            $script:PatchedCommentId = $null

            Set-GhAvailable -Available $true
            New-GhApiMock -Comments $comments -OnPatch {
                param($commentId)
                $script:PatchedCommentId = $commentId
            }

            $result = Publish-PrComment -Repo "test/repo" -PrNumber "123" -Marker "<!-- marker -->" -Body "New evidence"
            $result | Should -BeTrue
            $script:PatchedCommentId | Should -Be 102
        }

        It 'POSTs new comment when no existing comment matches marker' {
            $comments = @(
                @{ id = 101; body = "Unrelated comment"; user = @{ login = "bot-user" } }
            )

            $script:Posted = $false

            Set-GhAvailable -Available $true
            New-GhApiMock -Comments $comments -OnPost {
                $script:Posted = $true
            }

            $result = Publish-PrComment -Repo "test/repo" -PrNumber "123" -Marker "<!-- marker -->" -Body "New evidence"
            $result | Should -BeTrue
            $script:Posted | Should -BeTrue
        }

        It 'matches marker literally even when containing regex metacharacters' {
            $regexMarker = '<!-- [special] (marker.*+?^${}|) -->'
            $comments = @(
                @{ id = 201; body = "$regexMarker`nExisting evidence"; user = @{ login = "bot-user" } }
            )

            $script:PatchedCommentId = $null

            Set-GhAvailable -Available $true
            New-GhApiMock -Comments $comments -OnPatch {
                param($commentId)
                $script:PatchedCommentId = $commentId
            }

            $result = Publish-PrComment -Repo "test/repo" -PrNumber "123" -Marker $regexMarker -Body "New evidence"
            $result | Should -BeTrue
            $script:PatchedCommentId | Should -Be 201
        }

        It 'does not match when regex pattern would match but literal prefix does not' {
            # Regex '<!-- marker.* -->' would match '<!-- marker123 -->', but literal prefix check must not
            $patternMarker = '<!-- marker.* -->'
            $comments = @(
                @{ id = 202; body = "<!-- marker123 -->`nExisting evidence"; user = @{ login = "bot-user" } }
            )

            $script:Posted = $false
            $script:PatchedCommentId = $null

            Set-GhAvailable -Available $true
            New-GhApiMock -Comments $comments -OnPost {
                $script:Posted = $true
            } -OnPatch {
                param($commentId)
                $script:PatchedCommentId = $commentId
            }

            $result = Publish-PrComment -Repo "test/repo" -PrNumber "123" -Marker $patternMarker -Body "New evidence"
            $result | Should -BeTrue
            $script:Posted | Should -BeTrue
            $script:PatchedCommentId | Should -BeNullOrEmpty
        }

        It 'handles comments with null or missing body without throwing' {
            $commentsJson = '[{"id": 301, "body": null, "user": {"login": "bot-user"}}, {"id": 302, "user": {"login": "bot-user"}}]'
            $script:Posted = $false

            Set-GhAvailable -Available $true
            New-GhApiMock -Comments $commentsJson -OnPost {
                $script:Posted = $true
            }

            $result = Publish-PrComment -Repo "test/repo" -PrNumber "123" -Marker "<!-- marker -->" -Body "New evidence"
            $result | Should -BeTrue
            $script:Posted | Should -BeTrue
        }
    }

    Context 'Error resilience and non-terminating behavior' {
        It 'returns $false and emits warning when gh api list command fails' {
            Set-GhAvailable -Available $true
            New-GhApiMock -ExitCode 1

            $result = Publish-PrComment -Repo "test/repo" -PrNumber "123" -Marker "<!-- marker -->" -Body "New evidence"
            $result | Should -BeFalse
        }

        It 'returns $false and emits warning when gh api throws an exception' {
            Set-GhAvailable -Available $true
            New-GhApiMock -ThrowException "Simulated network failure"

            $result = Publish-PrComment -Repo "test/repo" -PrNumber "123" -Marker "<!-- marker -->" -Body "New evidence"
            $result | Should -BeFalse
        }
    }

    Context 'Temp file lifecycle and cleanup' {
        It 'leaves no temp files in GetTempPath on success' {
            $prefix = "test-cleanup-success-$([guid]::NewGuid().ToString().Substring(0,8))"

            Set-GhAvailable -Available $true
            New-GhApiMock

            $result = Publish-PrComment -Repo "test/repo" -PrNumber "123" -Marker "<!-- marker -->" -Body "New evidence" -TempFilePrefix $prefix
            $result | Should -BeTrue

            $remainingFiles = Get-ChildItem -Path ([System.IO.Path]::GetTempPath()) -Filter "$prefix*"
            $remainingFiles.Count | Should -Be 0
        }

        It 'leaves no temp files in GetTempPath on failure' {
            $prefix = "test-cleanup-fail-$([guid]::NewGuid().ToString().Substring(0,8))"

            Set-GhAvailable -Available $true
            New-GhApiMock -ThrowException "Simulated error during publish"

            $result = Publish-PrComment -Repo "test/repo" -PrNumber "123" -Marker "<!-- marker -->" -Body "New evidence" -TempFilePrefix $prefix
            $result | Should -BeFalse

            $remainingFiles = Get-ChildItem -Path ([System.IO.Path]::GetTempPath()) -Filter "$prefix*"
            $remainingFiles.Count | Should -Be 0
        }
    }

    Context 'Shared mock helper — New-GhApiMock exit code and exception paths' {
        It 'sets $LASTEXITCODE and returns empty string when -ExitCode is non-zero' {
            Set-GhAvailable -Available $true
            New-GhApiMock -ExitCode 42

            $output = gh api repos/test/repo/issues/123/comments
            $output | Should -BeNullOrEmpty
            $global:LASTEXITCODE | Should -Be 42
        }

        It 'throws specified exception message when -ThrowException is provided' {
            Set-GhAvailable -Available $true
            New-GhApiMock -ThrowException "Custom failure message"

            { gh api repos/test/repo/issues/123/comments } | Should -Throw -ExpectedMessage "Custom failure message"
        }
    }
}
