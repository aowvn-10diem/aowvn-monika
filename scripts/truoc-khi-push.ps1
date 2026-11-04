# Kiểm đủ hai flavor trước khi đẩy; giữ mã lỗi của bước không đạt.
$ErrorActionPreference = 'Stop'
# PowerShell 5 và 7 cùng kiểm mã thoát native một cách tường minh.
$PSNativeCommandUseErrorActionPreference = $false
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$taskStage = 'chuẩn bị'
$taskExitCode = 1
$taskPushedLocation = $false

try {
    $taskRoot = Split-Path -Parent $PSScriptRoot
    Push-Location -LiteralPath $taskRoot
    $taskPushedLocation = $true
    if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT) {
        $taskGradle = Join-Path $taskRoot 'gradlew.bat'
    } else {
        $taskGradle = Join-Path $taskRoot 'gradlew'
    }
    foreach ($taskStage in @('testDebugUnitTest', 'assembleDebug', 'assemblePlayDebug')) {
        & $taskGradle $taskStage --console=plain
        if ($LASTEXITCODE -ne 0) {
            $taskExitCode = $LASTEXITCODE
            throw "Bước $taskStage không đạt."
        }
    }
    $taskStage = 'quét APK Play'
    $taskScannerArgs = @('-X', 'utf8', 'scripts/check-play-apk.py',
        'app/build/outputs/apk/play/debug/app-play-universal-debug.apk')
    if (Get-Command py -ErrorAction SilentlyContinue) {
        & py -3 @taskScannerArgs
    } elseif (Get-Command python3 -ErrorAction SilentlyContinue) {
        & python3 @taskScannerArgs
    } else {
        & python @taskScannerArgs
    }
    if ($LASTEXITCODE -ne 0) {
        $taskExitCode = $LASTEXITCODE
        throw 'Quét APK Play không đạt.'
    }
    $taskExitCode = 0
} catch {
    if ($taskExitCode -eq 0) { $taskExitCode = 1 }
    [Console]::Error.WriteLine($_.Exception.Message)
} finally {
    if ($taskPushedLocation) { Pop-Location }
    if ($taskExitCode -eq 0) {
        [Console]::WriteLine('ĐẠT: test GitHub/Play, build debug/Play và quét APK Play.')
    } else {
        [Console]::WriteLine("LỖI: $taskStage (mã $taskExitCode).")
    }
}
exit $taskExitCode
