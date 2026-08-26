<#
.SYNOPSIS
    Static source-text regression assertions for .github/workflows/release.yml.
    Asserts presence of signing guards, apksigner directory/glob checks, and error annotations.
    Dot-sourced by Invoke-ScriptTests.ps1.
#>

$ReleaseWorkflowPath = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot "../../.github/workflows/release.yml")
)

Write-Host "--- static hygiene: release.yml ---"

Assert-True -Condition ([System.IO.File]::Exists($ReleaseWorkflowPath)) `
            -TestName "static: release.yml exists"

$WorkflowText = [System.IO.File]::ReadAllText($ReleaseWorkflowPath, [System.Text.Encoding]::UTF8)

# ---------------------------------------------------------------------------
# Keystore Decoding Guards (#259, #269, #272, #309, #318, #319, #324, #334)
# ---------------------------------------------------------------------------
Assert-True -Condition ($WorkflowText -match 'base64 -d 2>&1 > "\$KEYSTORE_PATH"') `
            -TestName "static: release.yml decodes keystore to KEYSTORE_PATH"

Assert-True -Condition ($WorkflowText -match 'DECODE_STDERR=\$\(echo "\$RELEASE_KEYSTORE_BASE64" \| base64 -d') `
            -TestName "static: release.yml captures base64 decode stderr into variable"

Assert-True -Condition ($WorkflowText -match '(?ms)::error::Failed to base64-decode RELEASE_KEYSTORE_BASE64 secret: \$DECODE_STDERR"?\s*\n\s*exit 1') `
            -TestName "static: release.yml contains keystore decode failure annotation"

Assert-True -Condition ($WorkflowText -match '\[ ! -s "\$KEYSTORE_PATH" \]') `
            -TestName "static: release.yml contains non-empty keystore file guard"

Assert-True -Condition ($WorkflowText -match '(?ms)::error::Decoded release keystore at \$KEYSTORE_PATH is missing or empty\."?\s*\n\s*exit 1') `
            -TestName "static: release.yml contains empty keystore file error annotation"

# ---------------------------------------------------------------------------
# apksigner Path Resolution & Directory Guards (#261, #269, #273, #311, #318, #325, #334)
# ---------------------------------------------------------------------------
Assert-True -Condition ($WorkflowText -match '\[ -z "\$\{ANDROID_HOME:-\}" \]') `
            -TestName "static: release.yml contains ANDROID_HOME unset guard"

Assert-True -Condition ($WorkflowText -match '(?ms)::error::ANDROID_HOME is unset\."?\s*\n\s*exit 1') `
            -TestName "static: release.yml contains ANDROID_HOME unset error annotation"

Assert-True -Condition ($WorkflowText -match '\[ ! -d "\$ANDROID_HOME/build-tools" \]') `
            -TestName "static: release.yml contains build-tools directory existence guard"

Assert-True -Condition ($WorkflowText -match '(?ms)::error::\$ANDROID_HOME/build-tools directory does not exist\."?\s*\n\s*exit 1') `
            -TestName "static: release.yml contains build-tools directory error annotation"

Assert-True -Condition ($WorkflowText -match 'BUILD_TOOLS_DIRS=\$\(ls -1d "\$ANDROID_HOME"/build-tools/\* 2>/dev/null \|\| true\)') `
            -TestName "static: release.yml discovers build-tools versions safely"

Assert-True -Condition ($WorkflowText -match '\[ -z "\$BUILD_TOOLS_DIRS" \]') `
            -TestName "static: release.yml contains empty build-tools discovery guard"

Assert-True -Condition ($WorkflowText -match '(?ms)::error::No build-tools versions found under \$ANDROID_HOME/build-tools/\."?\s*\n\s*exit 1') `
            -TestName "static: release.yml contains empty build-tools error annotation"

Assert-True -Condition ($WorkflowText -match '\[ ! -x "\$APKSIGNER" \]') `
            -TestName "static: release.yml checks apksigner executable existence"

# ---------------------------------------------------------------------------
# Debug Certificate Rejection (#259, #269, #319, #334)
# ---------------------------------------------------------------------------
Assert-True -Condition ($WorkflowText -match 'grep -q "CN=Android Debug"') `
            -TestName "static: release.yml inspects certificates for CN=Android Debug"

Assert-True -Condition ($WorkflowText -match '(?ms)::error::Packaged APK is signed with Android Debug certificate, not a release certificate\."?\s*\n\s*exit 1') `
            -TestName "static: release.yml contains debug certificate rejection error annotation"
