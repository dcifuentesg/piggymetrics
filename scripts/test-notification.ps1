# Verificacion de componente: notification-service, logica (RecipientServiceImpl, casos RSI-xx).
# Ejecuta la suite, la cobertura de ramas (JaCoCo) y la mutacion (PIT).
# Uso, desde la raiz del repo en PowerShell:
#     powershell -ExecutionPolicy Bypass -File scripts\test-notification.ps1
# Requisitos: JDK 8 en el PATH. Si Maven no esta instalado, el script descarga Maven 3.9.9 en .tools\ (una vez).
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$mod = "notification-service"

# --- Maven: el del sistema o uno local en .tools\
$mvn = (Get-Command mvn -ErrorAction SilentlyContinue).Source
if (-not $mvn) {
    $ver = "3.9.9"
    $tools = Join-Path $root ".tools"
    $mhome = Join-Path $tools "apache-maven-$ver"
    if (-not (Test-Path $mhome)) {
        Write-Host "Maven no encontrado; descargando Maven $ver en .tools\ ..."
        New-Item -ItemType Directory -Force -Path $tools | Out-Null
        $zip = Join-Path $tools "maven.zip"
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest "https://archive.apache.org/dist/maven/maven-3/$ver/binaries/apache-maven-$ver-bin.zip" -OutFile $zip
        Expand-Archive $zip -DestinationPath $tools -Force
        Remove-Item $zip
    }
    $mvn = Join-Path $mhome "bin\mvn.cmd"
}
& java -version
if ($LASTEXITCODE -ne 0) { throw "No se encontro java en el PATH. Abre una terminal nueva despues de instalar el JDK 8." }

function Run-Mvn([string[]]$mvnArgs) {
    & $mvn -B @mvnArgs
    if ($LASTEXITCODE -ne 0) { throw "Maven termino con codigo $LASTEXITCODE" }
}

$cls = "com.piggymetrics.notification.service.RecipientServiceImpl"
$pit = "org.pitest:pitest-maven:mutationCoverage"

Write-Host "`n== 1/3 Suite de LOGICA: RecipientServiceImpl (JUnit 4 + Mockito) + JaCoCo =="
Run-Mvn @("-pl", $mod, "clean")
Run-Mvn @("-pl", $mod, "-P", "component-tests", "test", "-Dtest=RecipientServiceImplComponentTest", "-DfailIfNoTests=false")
Write-Host "`n== Veredictos por caso de prueba =="
Get-Content "$mod\target\component-verdicts.txt"

Write-Host "`n== 2/3 Cobertura de ramas de RecipientServiceImpl (JaCoCo) =="
$csv = "$mod\target\site\jacoco\jacoco.csv"
Get-Content $csv -TotalCount 1
Select-String -Path $csv -Pattern ",RecipientServiceImpl," | ForEach-Object { $_.Line }

Write-Host "`n== 3/3 Mutacion (PIT): pruebas originales vs suite RSI, operadores por defecto y todos =="
foreach ($run in @(
    @("com.piggymetrics.notification.service.RecipientServiceImplTest", "DEFAULTS", "rsi-original"),
    @("com.piggymetrics.notification.service.RecipientServiceImplComponentTest", "DEFAULTS", "rsi"),
    @("com.piggymetrics.notification.service.RecipientServiceImplTest", "ALL", "rsi-original-all"),
    @("com.piggymetrics.notification.service.RecipientServiceImplComponentTest", "ALL", "rsi-all"))) {
    Run-Mvn @("-pl", $mod, "-P", "component-tests", $pit, "-DtargetClasses=$cls", "-DtargetTests=$($run[0])",
        "-Dmutators=$($run[1])", "-DreportsDirectory=target/pit-reports/$($run[2])")
}

Write-Host "`nReportes:"
Write-Host "  Veredictos: $mod\target\component-verdicts.txt"
Write-Host "  JaCoCo:     $mod\target\site\jacoco\index.html"
Write-Host "  PIT:        $mod\target\pit-reports\{rsi-original,rsi,rsi-original-all,rsi-all}\index.html"
