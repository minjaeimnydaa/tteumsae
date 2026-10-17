param(
    [string]$DeviceId = $env:TTEUMSAE_ANDROID_DEVICE
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($DeviceId)) {
    throw 'Pass -DeviceId or set TTEUMSAE_ANDROID_DEVICE to an adb device id.'
}

$maestro = (Get-Command maestro -ErrorAction Stop).Source
$flows = @(
    Join-Path $PSScriptRoot 'flows\visual-home-route.yaml'
    Join-Path $PSScriptRoot 'flows\visual-saved.yaml'
    Join-Path $PSScriptRoot 'flows\visual-settings-account.yaml'
)
$resultRoot = Join-Path $PSScriptRoot '..\android\app\build\maestro-visual-results'

foreach ($flow in $flows) {
    & $maestro check-syntax $flow
    if ($LASTEXITCODE -ne 0) {
        throw "Maestro syntax validation failed for $flow."
    }
}

& $maestro --device $DeviceId test `
    --test-output-dir $resultRoot `
    --format HTML `
    --output (Join-Path $resultRoot 'report.html') `
    @flows

if ($LASTEXITCODE -ne 0) {
    throw "Visual regression failed with exit code $LASTEXITCODE."
}
