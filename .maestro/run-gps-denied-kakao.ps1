param(
    [string]$DeviceId = $env:TTEUMSAE_ANDROID_DEVICE
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($DeviceId)) {
    throw 'Pass -DeviceId or set TTEUMSAE_ANDROID_DEVICE to an adb device id.'
}

$adb = (Get-Command adb -ErrorAction Stop).Source
$maestro = (Get-Command maestro -ErrorAction Stop).Source
$packageName = 'com.tteumsae.app'
$permissions = @(
    'android.permission.ACCESS_FINE_LOCATION',
    'android.permission.ACCESS_COARSE_LOCATION'
)

foreach ($permission in $permissions) {
    & $adb -s $DeviceId shell pm clear-permission-flags $packageName $permission user-set user-fixed
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to reset $permission on $DeviceId."
    }
}

try {
    & $maestro --device $DeviceId test "$PSScriptRoot\flows\gps-denied-kakao.yaml"
    if ($LASTEXITCODE -ne 0) {
        throw "Maestro regression failed with exit code $LASTEXITCODE."
    }
} finally {
    & $adb -s $DeviceId shell pm grant $packageName android.permission.ACCESS_COARSE_LOCATION
    & $adb -s $DeviceId shell pm grant $packageName android.permission.ACCESS_FINE_LOCATION
    foreach ($permission in $permissions) {
        & $adb -s $DeviceId shell pm clear-permission-flags $packageName $permission user-set user-fixed
    }
}
