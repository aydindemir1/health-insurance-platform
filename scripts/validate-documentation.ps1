[CmdletBinding()]
param(
    [switch]$SkipMermaid
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$originalLocation = Get-Location
$temporaryDirectory = $null

function Assert-Condition {
    param(
        [Parameter(Mandatory)]
        [bool]$Condition,

        [Parameter(Mandatory)]
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

try {
    Set-Location $repositoryRoot

    $markdownFiles = @((Get-Item 'README.md')) + @(Get-ChildItem 'docs' -Recurse -File -Filter '*.md')
    $brokenLinks = [System.Collections.Generic.List[string]]::new()
    $linkPattern = '!?(?:\[[^\]]*\])\((?<target>[^)]+)\)'

    foreach ($markdownFile in $markdownFiles) {
        $content = Get-Content -LiteralPath $markdownFile.FullName -Raw
        foreach ($match in [regex]::Matches($content, $linkPattern)) {
            $target = $match.Groups['target'].Value.Trim().Trim('<', '>')
            if ($target -match '^(?:https?://|mailto:|#)') {
                continue
            }

            $targetWithoutFragment = ($target -split '#', 2)[0]
            if ([string]::IsNullOrWhiteSpace($targetWithoutFragment)) {
                continue
            }

            $resolvedTarget = Join-Path $markdownFile.DirectoryName $targetWithoutFragment
            if (-not (Test-Path -LiteralPath $resolvedTarget)) {
                $relativeMarkdownPath = [System.IO.Path]::GetRelativePath($repositoryRoot, $markdownFile.FullName)
                $brokenLinks.Add("${relativeMarkdownPath}: $target")
            }
        }
    }

    Assert-Condition ($brokenLinks.Count -eq 0) ("Broken local Markdown links:`n" + ($brokenLinks -join "`n"))
    Write-Host "Markdown links: OK ($($markdownFiles.Count) files)"

    $jsonFiles = @(
        'demo/demo-data.json',
        'infra/keycloak/health-insurance-realm.json'
    )
    foreach ($jsonFile in $jsonFiles) {
        Get-Content -LiteralPath $jsonFile -Raw | ConvertFrom-Json | Out-Null
    }
    Write-Host "JSON syntax: OK ($($jsonFiles.Count) files)"

    $demoScripts = @(Get-ChildItem 'demo' -File -Filter '*.ps1')
    foreach ($demoScript in $demoScripts) {
        $tokens = $null
        $parseErrors = $null
        [System.Management.Automation.Language.Parser]::ParseFile(
            $demoScript.FullName,
            [ref]$tokens,
            [ref]$parseErrors
        ) | Out-Null
        Assert-Condition ($parseErrors.Count -eq 0) (
            "PowerShell syntax errors in $($demoScript.Name):`n" +
            (($parseErrors | ForEach-Object Message) -join "`n")
        )
    }
    Write-Host "Demo PowerShell syntax: OK ($($demoScripts.Count) files)"

    $expectedScreenshots = @(
        'docs/screenshots/01-dashboard.png',
        'docs/screenshots/02-pre-authorization-work-queue.png',
        'docs/screenshots/03-submit-pre-authorization.png',
        'docs/screenshots/04-pre-authorization-detail.png',
        'docs/screenshots/05-specialist-decision.png'
    )
    foreach ($screenshot in $expectedScreenshots) {
        Assert-Condition (Test-Path -LiteralPath $screenshot -PathType Leaf) "Missing screenshot: $screenshot"
        Assert-Condition ((Get-Item -LiteralPath $screenshot).Length -gt 0) "Empty screenshot: $screenshot"
    }
    Write-Host "Portfolio screenshots: OK ($($expectedScreenshots.Count) files)"

    $mermaidBlocks = [System.Collections.Generic.List[object]]::new()
    foreach ($markdownFile in $markdownFiles) {
        $content = Get-Content -LiteralPath $markdownFile.FullName -Raw
        $blockIndex = 0
        foreach ($match in [regex]::Matches($content, '(?ms)^```mermaid\s*\r?\n(.*?)\r?\n```')) {
            $blockIndex++
            $mermaidBlocks.Add([pscustomobject]@{
                Source = [System.IO.Path]::GetRelativePath($repositoryRoot, $markdownFile.FullName)
                Index = $blockIndex
                Content = $match.Groups[1].Value
            })
        }
    }

    Assert-Condition ($mermaidBlocks.Count -gt 0) 'No Mermaid diagrams were found.'

    if ($SkipMermaid) {
        Write-Host "Mermaid rendering: SKIPPED ($($mermaidBlocks.Count) blocks found)"
    }
    else {
        $temporaryDirectory = Join-Path ([System.IO.Path]::GetTempPath()) (
            'hip-documentation-validation-' + [guid]::NewGuid().ToString('N')
        )
        New-Item -ItemType Directory -Path $temporaryDirectory | Out-Null

        $diagramNumber = 0
        foreach ($block in $mermaidBlocks) {
            $diagramNumber++
            $diagramInput = Join-Path $temporaryDirectory "$diagramNumber.mmd"
            $diagramOutput = Join-Path $temporaryDirectory "$diagramNumber.svg"
            Set-Content -LiteralPath $diagramInput -Value $block.Content

            & npm exec --yes --package=@mermaid-js/mermaid-cli -- mmdc -i $diagramInput -o $diagramOutput
            Assert-Condition ($LASTEXITCODE -eq 0) (
                "Mermaid rendering failed for $($block.Source), block $($block.Index)."
            )
            Assert-Condition (Test-Path -LiteralPath $diagramOutput -PathType Leaf) (
                "Mermaid output was not created for $($block.Source), block $($block.Index)."
            )
        }

        Write-Host "Mermaid rendering: OK ($($mermaidBlocks.Count) blocks)"
    }

    Write-Host 'Documentation validation completed successfully.'
}
finally {
    Set-Location $originalLocation

    if ($null -ne $temporaryDirectory -and (Test-Path -LiteralPath $temporaryDirectory)) {
        $resolvedTemporaryDirectory = [System.IO.Path]::GetFullPath($temporaryDirectory)
        $systemTemporaryRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
        $isExpectedTemporaryPath =
            $resolvedTemporaryDirectory.StartsWith($systemTemporaryRoot, [System.StringComparison]::OrdinalIgnoreCase) -and
            ([System.IO.Path]::GetFileName($resolvedTemporaryDirectory)).StartsWith(
                'hip-documentation-validation-',
                [System.StringComparison]::Ordinal
            )

        if ($isExpectedTemporaryPath) {
            [System.IO.Directory]::Delete($resolvedTemporaryDirectory, $true)
        }
    }
}
