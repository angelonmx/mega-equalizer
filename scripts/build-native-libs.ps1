#Requires -Version 5.1
<#
.SYNOPSIS
    Recompila libmega_abi.so para arm64-v8a, armeabi-v7a y x86_64.

.DESCRIPTION
    La biblioteca nativa solo identifica la arquitectura instalada para que el
    actualizador elija el APK correcto. Usa el NDK de Android y produce
    bibliotecas alineadas a páginas de 16 KB.

    Los binarios ya están incluidos en app/src/main/jniLibs. Este script es
    necesario únicamente si se modifica app/src/main/jni/mega_abi.c o se quiere
    reconstruir con otra versión del NDK.

.PARAMETER NdkPath
    Ruta al NDK. Si se omite, se busca ANDROID_NDK_HOME, ANDROID_HOME\ndk o
    %LOCALAPPDATA%\Android\Sdk\ndk.
#>
param(
    [string]$NdkPath = ""
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$sourceFile = Join-Path $projectRoot "app\src\main\jni\mega_abi.c"
$jniLibs = Join-Path $projectRoot "app\src\main\jniLibs"

if (-not (Test-Path -LiteralPath $sourceFile)) {
    throw "No se encontró el código fuente: $sourceFile"
}

function Find-Ndk {
    param([string]$Explicit)

    if ($Explicit) {
        return (Resolve-Path -LiteralPath $Explicit).Path
    }
    if ($env:ANDROID_NDK_HOME -and (Test-Path -LiteralPath $env:ANDROID_NDK_HOME)) {
        return (Resolve-Path -LiteralPath $env:ANDROID_NDK_HOME).Path
    }

    $candidates = @()
    if ($env:ANDROID_HOME) {
        $candidates += $env:ANDROID_HOME
    }
    if ($env:ANDROID_SDK_ROOT) {
        $candidates += $env:ANDROID_SDK_ROOT
    }
    $candidates += (Join-Path $env:LOCALAPPDATA "Android\Sdk")

    foreach ($sdk in $candidates) {
        $ndkRoot = Join-Path $sdk "ndk"
        if (-not (Test-Path -LiteralPath $ndkRoot)) {
            continue
        }
        $latest = Get-ChildItem -LiteralPath $ndkRoot -Directory |
            Sort-Object Name -Descending |
            Select-Object -First 1
        if ($latest) {
            return $latest.FullName
        }
    }

    throw "No se encontró el NDK. Instala el NDK desde Android Studio o pasa -NdkPath."
}

$resolvedNdk = Find-Ndk -Explicit $NdkPath
Write-Host "NDK: $resolvedNdk"

if ($IsWindows -or $env:OS -eq "Windows_NT") {
    $hostTag = "windows-x86_64"
    $exeSuffix = ".cmd"
} else {
    $hostTag = "${platform}-x86_64"
    $exeSuffix = ""
}

$toolchainBin = Join-Path $resolvedNdk "toolchains\llvm\prebuilt\$hostTag\bin"
if (-not (Test-Path -LiteralPath $toolchainBin)) {
    throw "No se encontró el toolchain del NDK en $toolchainBin"
}

$targets = @{
    "arm64-v8a"   = "aarch64-linux-android26"
    "armeabi-v7a" = "armv7a-linux-androideabi26"
    "x86_64"      = "x86_64-linux-android26"
}

$strip = Join-Path $toolchainBin "llvm-strip.exe"
if (-not (Test-Path -LiteralPath $strip)) {
    $strip = Join-Path $toolchainBin "llvm-strip"
}

foreach ($abi in @("arm64-v8a", "armeabi-v7a", "x86_64")) {
    $target = $targets[$abi]
    $clang = Join-Path $toolchainBin "$target-clang$exeSuffix"
    if (-not (Test-Path -LiteralPath $clang)) {
        $clang = Join-Path $toolchainBin "$target-clang"
    }
    if (-not (Test-Path -LiteralPath $clang)) {
        throw "No se encontró el compilador para $abi en $toolchainBin"
    }

    $outputDir = Join-Path $jniLibs $abi
    New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
    $outputFile = Join-Path $outputDir "libmega_abi.so"

    Write-Host "Compilando $abi..."
    & $clang `
        -shared -fPIC -O2 -Wall -Wextra `
        -Wl,-z,max-page-size=16384 `
        -Wl,-soname,libmega_abi.so `
        -o $outputFile `
        $sourceFile
    if ($LASTEXITCODE -ne 0) {
        throw "La compilación falló para $abi (código $LASTEXITCODE)"
    }

    if (Test-Path -LiteralPath $strip) {
        & $strip --strip-unneeded $outputFile
        if ($LASTEXITCODE -ne 0) {
            throw "No se pudo optimizar la biblioteca de $abi"
        }
    }

    $size = (Get-Item -LiteralPath $outputFile).Length
    Write-Host "  $outputFile ($size bytes)"
}

Write-Host "Bibliotecas nativas actualizadas correctamente."
