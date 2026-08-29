<#
.SYNOPSIS
    Pester v5 test suite for scripts/lib/GitHubArtifactHelper.ps1.
    Tests SHA resolution, local SHA-caching, network skip on cache hits,
    CI/Release artifact download fallbacks, directory cleanup, and error handling.
#>

[Diagnostics.CodeAnalysis.SuppressMessageAttribute('PSUseShouldProcessForStateChangingFunctions', '', Justification = 'Test helper functions')]
[Diagnostics.CodeAnalysis.SuppressMessageAttribute('PSReviewUnusedParameter', '', Justification = 'Mock parameters accessed via closure or dynamic dispatch')]
[Diagnostics.CodeAnalysis.SuppressMessageAttribute('PSUseDeclaredVarsMoreThanAssignments', '', Justification = 'Test assertion tracking variables updated in closures')]
param()

BeforeAll {
    Import-Module Pester -ErrorAction SilentlyContinue
    . (Join-Path (Join-Path $PSScriptRoot "..") "lib\GitHubArtifactHelper.ps1")

    function Set-GhAvailable {
        [CmdletBinding()]
        [Diagnostics.CodeAnalysis.SuppressMessageAttribute('PSUseShouldProcessForStateChangingFunctions', '')]
        param([bool]$Available = $true)
        if ($Available) {
            Mock -CommandName Get-Command -MockWith { return [PSCustomObject]@{ Name = 'gh' } } -ParameterFilter { $Name -eq 'gh' }
        } else {
            Mock -CommandName Get-Command -MockWith { return $null } -ParameterFilter { $Name -eq 'gh' }
        }
    }

    function New-GhArtifactMock {
        [CmdletBinding()]
        [Diagnostics.CodeAnalysis.SuppressMessageAttribute('PSUseShouldProcessForStateChangingFunctions', '')]
        [Diagnostics.CodeAnalysis.SuppressMessageAttribute('PSReviewUnusedParameter', '')]
        param(
            [string]$Sha = "a1b2c3d4e5f6789012345678901234567890abcd",
            [string]$WorkflowRunsJson = $null,
            [int]$ExitCode = 0,
            [string]$ThrowException = $null,
            [scriptblock]$OnRunDownload = $null,
            [scriptblock]$OnReleaseDownload = $null
        )

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

            # Commit SHA endpoint
            if ($argsList -match 'api repos/.+/commits/.+') {
                $global:LASTEXITCODE = 0
                return $Sha
            }

            # Workflow runs endpoint
            if ($argsList -match 'api repos/.+/actions/runs') {
                $global:LASTEXITCODE = 0
                if ($null -ne $WorkflowRunsJson) {
                    return $WorkflowRunsJson
                }
                return '{"workflow_runs":[]}'
            }

            # gh run download
            if ($argsList -match 'run download') {
                if ($OnRunDownload) {
                    & $OnRunDownload $argsList $args
                }
                $global:LASTEXITCODE = 0
                return ''
            }

            # gh release download
            if ($argsList -match 'release download') {
                if ($OnReleaseDownload) {
                    & $OnReleaseDownload $argsList $args
                }
                $global:LASTEXITCODE = 0
                return ''
            }

            return ''
        }.GetNewClosure()

        Mock -CommandName gh -MockWith $mockScript
    }
}

Describe 'GitHubArtifactHelper' {
    Context 'Get-LatestMainCommitSha' {
        It 'returns 7-character short SHA when gh api returns 40-character full SHA' {
            Set-GhAvailable -Available $true
            New-GhArtifactMock -Sha "a1b2c3d4e5f6789012345678901234567890abcd"

            $result = Get-LatestMainCommitSha -Repo "test/repo" -Branch "main"
            $result | Should -Be "a1b2c3d"
        }

        It 'returns exact string when gh api returns SHA with length <= 7' {
            Set-GhAvailable -Available $true
            New-GhArtifactMock -Sha "a1b2c3d"

            $result = Get-LatestMainCommitSha -Repo "test/repo" -Branch "main"
            $result | Should -Be "a1b2c3d"
        }

        It 'returns $null and emits warning when gh CLI is not available' {
            Set-GhAvailable -Available $false
            Mock -CommandName gh -MockWith { throw "gh should not be called" }

            $result = Get-LatestMainCommitSha -Repo "test/repo" -Branch "main"
            $result | Should -BeNullOrEmpty
            Should -Invoke -CommandName gh -Times 0 -Exactly
        }

        It 'returns $null when gh api fails with non-zero exit code' {
            Set-GhAvailable -Available $true
            New-GhArtifactMock -ExitCode 1

            $result = Get-LatestMainCommitSha -Repo "test/repo" -Branch "main"
            $result | Should -BeNullOrEmpty
        }

        It 'returns $null when gh api throws an exception' {
            Set-GhAvailable -Available $true
            New-GhArtifactMock -ThrowException "Network timeout"

            $result = Get-LatestMainCommitSha -Repo "test/repo" -Branch "main"
            $result | Should -BeNullOrEmpty
        }
    }

    Context 'Acceptance Criteria: Cached APK skips network download' {
        It 'returns the cached file path and skips download calls when cached APK exists' {
            $testDir = Join-Path ([System.IO.Path]::GetTempPath()) "cache-hit-test-$([guid]::NewGuid())"
            $apkCacheDir = Join-Path $testDir "ci-main"
            [System.IO.Directory]::CreateDirectory($apkCacheDir) | Out-Null
            $cachedFile = Join-Path $apkCacheDir "app-debug-a1b2c3d.apk"
            [System.IO.File]::WriteAllText($cachedFile, "fake apk binary")

            try {
                Set-GhAvailable -Available $true
                $tracking = [PSCustomObject]@{ RunDownloadCalled = $false; ReleaseDownloadCalled = $false }

                New-GhArtifactMock -Sha "a1b2c3d" `
                    -OnRunDownload { $tracking.RunDownloadCalled = $true } `
                    -OnReleaseDownload { $tracking.ReleaseDownloadCalled = $true }

                $result = Get-LatestMainBuildApk -Repo "test/repo" -Branch "main" -CacheDir $apkCacheDir

                $result | Should -Be $cachedFile
                Test-Path $result | Should -BeTrue
                $tracking.RunDownloadCalled | Should -BeFalse
                $tracking.ReleaseDownloadCalled | Should -BeFalse
            } finally {
                if (Test-Path $testDir) {
                    Remove-Item -Path $testDir -Recurse -Force -ErrorAction SilentlyContinue
                }
            }
        }

        It 'uses provided -Sha parameter and skips commit SHA fetch when -Sha is supplied' {
            $testDir = Join-Path ([System.IO.Path]::GetTempPath()) "cache-hit-sha-$([guid]::NewGuid())"
            $apkCacheDir = Join-Path $testDir "ci-main"
            [System.IO.Directory]::CreateDirectory($apkCacheDir) | Out-Null
            $cachedFile = Join-Path $apkCacheDir "app-debug-f9e8d7c.apk"
            [System.IO.File]::WriteAllText($cachedFile, "fake apk")

            try {
                Set-GhAvailable -Available $true
                Mock -CommandName gh -MockWith { throw "gh should not be called when cached and Sha is supplied" }

                $result = Get-LatestMainBuildApk -Repo "test/repo" -Sha "f9e8d7c" -CacheDir $apkCacheDir

                $result | Should -Be $cachedFile
                Should -Invoke -CommandName gh -Times 0 -Exactly
            } finally {
                if (Test-Path $testDir) {
                    Remove-Item -Path $testDir -Recurse -Force -ErrorAction SilentlyContinue
                }
            }
        }
    }

    Context 'Cache miss and remote artifact retrieval' {
        It 'downloads from GitHub Actions CI artifact when matching run exists' {
            $testDir = Join-Path ([System.IO.Path]::GetTempPath()) "cache-miss-ci-$([guid]::NewGuid())"
            $apkCacheDir = Join-Path $testDir "ci-main"

            try {
                Set-GhAvailable -Available $true
                $runsJson = '{"workflow_runs":[{"id":998877,"head_sha":"a1b2c3d4e5f6"}]}'

                New-GhArtifactMock -Sha "a1b2c3d" -WorkflowRunsJson $runsJson `
                    -OnRunDownload {
                        param($argsStr, $argsArr)
                        for ($i = 0; $i -lt $argsArr.Count; $i++) {
                            if ($argsArr[$i] -eq '--dir' -and ($i + 1) -lt $argsArr.Count) {
                                $dir = $argsArr[$i + 1]
                                $apkFile = Join-Path $dir "downloaded-ci.apk"
                                [System.IO.File]::WriteAllText($apkFile, "ci apk binary content")
                                break
                            }
                        }
                    }

                $result = Get-LatestMainBuildApk -Repo "test/repo" -Branch "main" -CacheDir $apkCacheDir

                $expectedCached = Join-Path $apkCacheDir "app-debug-a1b2c3d.apk"
                $result | Should -Be $expectedCached
                Test-Path $result | Should -BeTrue
                Get-Content $result | Should -Be "ci apk binary content"
            } finally {
                if (Test-Path $testDir) {
                    Remove-Item -Path $testDir -Recurse -Force -ErrorAction SilentlyContinue
                }
            }
        }

        It 'falls back to GitHub Release download when no matching CI run exists' {
            $testDir = Join-Path ([System.IO.Path]::GetTempPath()) "cache-miss-rel-$([guid]::NewGuid())"
            $apkCacheDir = Join-Path $testDir "ci-main"

            try {
                Set-GhAvailable -Available $true
                $runsJson = '{"workflow_runs":[]}'

                New-GhArtifactMock -Sha "a1b2c3d" -WorkflowRunsJson $runsJson `
                    -OnReleaseDownload {
                        param($argsStr, $argsArr)
                        for ($i = 0; $i -lt $argsArr.Count; $i++) {
                            if ($argsArr[$i] -eq '--dir' -and ($i + 1) -lt $argsArr.Count) {
                                $dir = $argsArr[$i + 1]
                                $apkFile = Join-Path $dir "release.apk"
                                [System.IO.File]::WriteAllText($apkFile, "release apk binary content")
                                break
                            }
                        }
                    }

                $result = Get-LatestMainBuildApk -Repo "test/repo" -Branch "main" -CacheDir $apkCacheDir

                $expectedCached = Join-Path $apkCacheDir "app-debug-a1b2c3d.apk"
                $result | Should -Be $expectedCached
                Test-Path $result | Should -BeTrue
                Get-Content $result | Should -Be "release apk binary content"
            } finally {
                if (Test-Path $testDir) {
                    Remove-Item -Path $testDir -Recurse -Force -ErrorAction SilentlyContinue
                }
            }
        }

        It 'bypasses existing cached APK when -Force switch is supplied' {
            $testDir = Join-Path ([System.IO.Path]::GetTempPath()) "cache-force-$([guid]::NewGuid())"
            $apkCacheDir = Join-Path $testDir "ci-main"
            [System.IO.Directory]::CreateDirectory($apkCacheDir) | Out-Null
            $cachedFile = Join-Path $apkCacheDir "app-debug-a1b2c3d.apk"
            [System.IO.File]::WriteAllText($cachedFile, "old cached content")

            try {
                Set-GhAvailable -Available $true
                $runsJson = '{"workflow_runs":[{"id":1234,"head_sha":"a1b2c3d"}]}'

                New-GhArtifactMock -Sha "a1b2c3d" -WorkflowRunsJson $runsJson `
                    -OnRunDownload {
                        param($argsStr, $argsArr)
                        for ($i = 0; $i -lt $argsArr.Count; $i++) {
                            if ($argsArr[$i] -eq '--dir' -and ($i + 1) -lt $argsArr.Count) {
                                $dir = $argsArr[$i + 1]
                                $apkFile = Join-Path $dir "fresh.apk"
                                [System.IO.File]::WriteAllText($apkFile, "freshly downloaded apk")
                                break
                            }
                        }
                    }

                $result = Get-LatestMainBuildApk -Repo "test/repo" -Branch "main" -CacheDir $apkCacheDir -Force

                $result | Should -Be $cachedFile
                Get-Content $result | Should -Be "freshly downloaded apk"
            } finally {
                if (Test-Path $testDir) {
                    Remove-Item -Path $testDir -Recurse -Force -ErrorAction SilentlyContinue
                }
            }
        }
    }

    Context 'Error resilience and cleanup' {
        It 'returns $null when gh CLI is missing from PATH' {
            $testDir = Join-Path ([System.IO.Path]::GetTempPath()) "no-gh-test-$([guid]::NewGuid())"
            $apkCacheDir = Join-Path $testDir "ci-main"

            try {
                Set-GhAvailable -Available $false
                Mock -CommandName gh -MockWith { throw "gh should not be called" }

                $result = Get-LatestMainBuildApk -Repo "test/repo" -Sha "a1b2c3d" -CacheDir $apkCacheDir
                $result | Should -BeNullOrEmpty
            } finally {
                if (Test-Path $testDir) {
                    Remove-Item -Path $testDir -Recurse -Force -ErrorAction SilentlyContinue
                }
            }
        }

        It 'returns $null when commit SHA resolution fails' {
            $testDir = Join-Path ([System.IO.Path]::GetTempPath()) "sha-fail-test-$([guid]::NewGuid())"
            $apkCacheDir = Join-Path $testDir "ci-main"

            try {
                Set-GhAvailable -Available $true
                New-GhArtifactMock -ExitCode 1

                $result = Get-LatestMainBuildApk -Repo "test/repo" -CacheDir $apkCacheDir
                $result | Should -BeNullOrEmpty
            } finally {
                if (Test-Path $testDir) {
                    Remove-Item -Path $testDir -Recurse -Force -ErrorAction SilentlyContinue
                }
            }
        }

        It 'returns $null and cleans up temp directory when download succeeds but contains no APK' {
            $testDir = Join-Path ([System.IO.Path]::GetTempPath()) "no-apk-test-$([guid]::NewGuid())"
            $apkCacheDir = Join-Path $testDir "ci-main"

            try {
                Set-GhAvailable -Available $true
                $runsJson = '{"workflow_runs":[{"id":5544,"head_sha":"a1b2c3d"}]}'

                New-GhArtifactMock -Sha "a1b2c3d" -WorkflowRunsJson $runsJson `
                    -OnRunDownload {
                        param($argsStr, $argsArr)
                        for ($i = 0; $i -lt $argsArr.Count; $i++) {
                            if ($argsArr[$i] -eq '--dir' -and ($i + 1) -lt $argsArr.Count) {
                                $dir = $argsArr[$i + 1]
                                $txtFile = Join-Path $dir "not-an-apk.txt"
                                [System.IO.File]::WriteAllText($txtFile, "just text")
                                break
                            }
                        }
                    }

                $result = Get-LatestMainBuildApk -Repo "test/repo" -Sha "a1b2c3d" -CacheDir $apkCacheDir
                $result | Should -BeNullOrEmpty
            } finally {
                if (Test-Path $testDir) {
                    Remove-Item -Path $testDir -Recurse -Force -ErrorAction SilentlyContinue
                }
            }
        }
    }
}
