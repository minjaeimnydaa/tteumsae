# Read-only: prints evidence JSON; never writes cache/project files or resolves dependencies.
param([string]$Repository = 'C:\dev\tteumsae', [string]$Cache = 'C:\Users\shjb0\.gradle\caches\modules-2\files-2.1')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
[xml]$model = Get-Content -Raw -LiteralPath (Join-Path $Repository 'android/app/build/intermediates/lint_vital_report_lint_model/release/generateReleaseLintVitalReportModel/release-artifact-dependencies.xml')
$coordinates = $model.dependencies.package.roots.Split(',') | Sort-Object -Unique
function Read-LicenseEntries($archive, [string]$prefix) {
  foreach ($entry in $archive.Entries) {
    if ($entry.Length -gt 0 -and $entry.FullName -match '(?i)(^|/)(LICENSE[^/]*|NOTICE[^/]*|COPYING[^/]*|OFL[^/]*)$|\.license$') {
      $reader = [System.IO.StreamReader]::new($entry.Open())
      try { [pscustomobject]@{ source = $prefix + $entry.FullName; text = $reader.ReadToEnd() } } finally { $reader.Dispose() }
    }
    if ($entry.FullName -eq 'classes.jar') {
      $stream = [System.IO.MemoryStream]::new()
      $inputStream = $entry.Open()
      try {
        $inputStream.CopyTo($stream); $stream.Position = 0
        $nested = [System.IO.Compression.ZipArchive]::new($stream, [System.IO.Compression.ZipArchiveMode]::Read)
        try { Read-LicenseEntries $nested ($prefix + 'classes.jar!/') } finally { $nested.Dispose() }
      } finally { $inputStream.Dispose(); $stream.Dispose() }
    }
  }
}
$evidence = foreach ($coordinate in $coordinates) {
  $parts = $coordinate.Split(':'); $version = $parts[2].Split('@')[0]; $ext = $parts[2].Split('@')[1]
  $moduleDir = Join-Path $Cache ($parts[0] + '/' + $parts[1] + '/' + $version)
  $files = @(Get-ChildItem -LiteralPath $moduleDir -Recurse -File)
  $pomFile = $files | Where-Object Extension -eq '.pom' | Select-Object -First 1
  $pom = if ($pomFile) { [xml](Get-Content -Raw -LiteralPath $pomFile.FullName) } else { $null }
  $licenses = @($pom.project.licenses.license | Where-Object { $_ } | ForEach-Object { @{name=[string]$_.name;url=[string]$_.url} })
  $artifact = $files | Where-Object { $_.Extension -eq ('.' + $ext) -and $_.Name -notmatch '-(sources|javadoc)\.' } | Select-Object -First 1
  $notices = if ($artifact) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($artifact.FullName)
    try { @(Read-LicenseEntries $zip '') } finally { $zip.Dispose() }
  } else { @() }
  [pscustomobject]@{ coordinate=$coordinate; url=[string]$pom.project.url; licenses=$licenses; notices=@($notices); pomFound=[bool]$pom; artifactFound=[bool]$artifact }
}
@{ modules=@($evidence); font=(Get-Content -Raw -LiteralPath (Join-Path $Repository 'android/third_party_licenses/Pretendard-OFL.txt')) } | ConvertTo-Json -Depth 10 -Compress
