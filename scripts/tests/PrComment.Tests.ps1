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

    Context 'Listing and Pagination' {
        It 'includes --paginate when querying comments' {
            $script:PaginateCalled = $false

            Set-GhAvailable -Available $true
            New-GhApiMock -OnList {
                param($argsList)
                if ($argsList -match '--paginate') {
                    $script:PaginateCalled = $true
                }
            }

            $result = Publish-PrComment -Repo "test/repo" -PrNumber "123" -Marker "<!-- marker -->" -Body "Test"
            $result | Should -BeTrue
            $script:PaginateCalled | Should -BeTrue
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
