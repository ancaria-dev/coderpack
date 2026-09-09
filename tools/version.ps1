<#
.SYNOPSIS
    Prints or sets the version of api and zygote, everywhere it is written.

.DESCRIPTION
    gradle.properties is the source of truth. Everywhere else that repeats the
    number by hand (the Javadoc in Api.java, the three READMEs) gets the
    same literal replacement, so a release does not depend on remembering
    which files happen to say "0.99.0" today.

    Run with no argument to print the current version. Pass a new one to bump
    it everywhere in one pass.

.EXAMPLE
    pwsh tools/version.ps1
    pwsh tools/version.ps1 0.99.1
#>
param(
    [string]$Version
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$propsPath = Join-Path $root 'gradle.properties'

$match = Select-String -Path $propsPath -Pattern '^version=(.+)$'
if (-not $match) { throw "No version= line in $propsPath" }
$current = $match.Matches[0].Groups[1].Value

if (-not $Version) {
    Write-Host $current
    return
}

$targets = @(
    (Join-Path $root 'gradle.properties'),
    (Join-Path $root 'api/src/main/java/dev/ancaria/coderpack/api/Api.java'),
    (Join-Path $root 'README.md'),
    (Join-Path $root 'README.EN.md'),
    (Join-Path $root 'README.DE.md')
)

$pattern = "(?<!\d)$([regex]::Escape($current))(?!\d)"
$touched = 0
foreach ($path in $targets) {
    $text = Get-Content -Path $path -Raw
    $new = [regex]::Replace($text, $pattern, $Version)
    if ($new -eq $text) {
        Write-Warning "$current not found in $path, left untouched"
        continue
    }
    Set-Content -Path $path -Value $new -NoNewline
    $touched++
}

Write-Host "$current -> $Version in $touched file(s)"
