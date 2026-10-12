<#
.SYNOPSIS
  Instala o Eclipse Temurin JDK (pacote ZIP, sem instalador) em E:\Java e configura JAVA_HOME e PATH.

.DESCRIPTION
  - Sem Administrador: grava JAVA_HOME/PATH no escopo do USUARIO (permanente, vale para o seu usuario).
  - Como Administrador: grava no escopo do SISTEMA (vale para todos os usuarios).
  O ZIP e baixado da API oficial da Adoptium e o SHA-256 e conferido antes de extrair.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\scripts\setup-jdk.ps1
  powershell -ExecutionPolicy Bypass -File .\scripts\setup-jdk.ps1 -Version 17
#>
param(
    [ValidateSet(17, 21)] [int]$Version = 21,
    [string]$InstallRoot = 'E:\Java'
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'   # Invoke-WebRequest fica muito mais rapido sem barra de progresso
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$target  = Join-Path $InstallRoot "jdk-$Version"
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
$scope   = if ($isAdmin) { 'Machine' } else { 'User' }
$regPath = if ($isAdmin) { 'HKLM:\SYSTEM\CurrentControlSet\Control\Session Manager\Environment' } else { 'HKCU:\Environment' }

# --- 1 e 2. Download do ZIP + verificacao + extracao -------------------------------------------
if (Test-Path (Join-Path $target 'bin\java.exe')) {
    Write-Host "JDK ja presente em $target - pulando download."
} else {
    $api = "https://api.adoptium.net/v3/assets/latest/$Version/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse"
    $pkg = (Invoke-RestMethod $api)[0].binary.package   # 'package' = ZIP ('installer' seria o MSI)
    if ($pkg.name -notlike '*.zip') { throw "Pacote inesperado: $($pkg.name)" }

    $tmp = Join-Path $InstallRoot '_download'
    New-Item -ItemType Directory -Force -Path $tmp | Out-Null
    $zip = Join-Path $tmp $pkg.name

    Write-Host "Baixando $($pkg.name) ($([math]::Round($pkg.size / 1MB)) MB)..."
    Invoke-WebRequest -Uri $pkg.link -OutFile $zip -UseBasicParsing

    $hash = (Get-FileHash $zip -Algorithm SHA256).Hash
    if ($hash -ne $pkg.checksum) { throw "SHA-256 nao confere. Esperado $($pkg.checksum), obtido $hash" }
    Write-Host 'SHA-256 verificado.'

    $extract = Join-Path $tmp 'extract'
    New-Item -ItemType Directory -Force -Path $extract | Out-Null
    tar.exe -xf $zip -C $extract                      # tar nativo do Windows 10+, bem mais rapido que Expand-Archive
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao extrair o ZIP.' }

    $inner = Get-ChildItem $extract -Directory | Select-Object -First 1   # ex.: jdk-21.0.x+y
    Move-Item $inner.FullName $target
    Remove-Item $tmp -Recurse -Force
    Write-Host "JDK extraido em $target"
}

# --- 3. Variaveis de ambiente permanentes -------------------------------------------------------
# PATH e lido/escrito direto no registro como REG_EXPAND_SZ para nao "achatar" outras entradas com %VAR%.
$key     = Get-Item $regPath
$rawPath = [string]$key.GetValue('Path', '', 'DoNotExpandEnvironmentNames')
$entries = $rawPath -split ';' | Where-Object { $_ }
if ($entries -notcontains '%JAVA_HOME%\bin') {
    $newPath = (@('%JAVA_HOME%\bin') + $entries) -join ';'
    Set-ItemProperty -Path $regPath -Name 'Path' -Value $newPath -Type ExpandString
    Write-Host "%JAVA_HOME%\bin adicionado ao PATH ($scope)."
} else {
    Write-Host "%JAVA_HOME%\bin ja estava no PATH ($scope)."
}
# Por ultimo: SetEnvironmentVariable tambem avisa o Windows (WM_SETTINGCHANGE) que o ambiente mudou.
[Environment]::SetEnvironmentVariable('JAVA_HOME', $target, $scope)
Write-Host "JAVA_HOME=$target ($scope)"

# --- 4. Verificacao na sessao atual -------------------------------------------------------------
$env:JAVA_HOME = $target
$env:Path      = "$target\bin;$env:Path"
Write-Host "`n> java -version";  cmd /c "java -version 2>&1"
Write-Host "`n> javac -version"; cmd /c "javac -version 2>&1"
Write-Host "`nPronto. Feche e reabra terminais/IDE para que eles enxerguem as novas variaveis."
