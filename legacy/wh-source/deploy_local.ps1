$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot

Write-Host "🚧 Starting Local Deployment Build..." -ForegroundColor Cyan

# 1. Frontend Build
Write-Host "📦 Building Frontend (Vue)..." -ForegroundColor Yellow
$FrontendDir = Join-Path $ProjectRoot "frontend"

Write-Host "  → Installing dependencies..." -ForegroundColor Yellow
$installProcess = Start-Process -FilePath "npm.cmd" -ArgumentList "install" -WorkingDirectory $FrontendDir -NoNewWindow -PassThru -Wait
if ($installProcess.ExitCode -ne 0) { throw "Frontend dependency install failed" }

Write-Host "  → Building..." -ForegroundColor Yellow
$buildProcess = Start-Process -FilePath "npm.cmd" -ArgumentList "run","build" -WorkingDirectory $FrontendDir -NoNewWindow -PassThru -Wait
if ($buildProcess.ExitCode -ne 0) { throw "Frontend build failed" }

# 2. Copy Static Files
Write-Host "copying static files to backend..." -ForegroundColor Yellow
$BackendStaticDir = Join-Path $ProjectRoot "backend\src\main\resources\static"
$FrontendDistDir = Join-Path $FrontendDir "dist"

if (Test-Path $BackendStaticDir) {
    Remove-Item -Path "$BackendStaticDir\*" -Recurse -Force
} else {
    New-Item -ItemType Directory -Force -Path $BackendStaticDir | Out-Null
}

Copy-Item -Path "$FrontendDistDir\*" -Destination $BackendStaticDir -Recurse -Force

# 3. Backend Build
Write-Host "building backend (Spring Boot)..." -ForegroundColor Yellow
$BackendDir = Join-Path $ProjectRoot "backend"

$mvnProcess = Start-Process -FilePath "cmd" -ArgumentList "/c","mvnw.cmd clean package -DskipTests" -WorkingDirectory $BackendDir -NoNewWindow -PassThru -Wait
if ($mvnProcess.ExitCode -ne 0) { throw "backend build failed" }

Write-Host "build completed!" -ForegroundColor Green
Write-Host "run backend:"
Write-Host "   cd backend"
Write-Host "   java -jar target\reputation-mvp-backend-0.0.1-SNAPSHOT.jar"
Write-Host ""
Write-Host "deploy way (use Ngrok):"
Write-Host "   1. run backend"
Write-Host "   2. install and run ngrok: ngrok http 8081"
Write-Host "   3. send https address to others to install PWA"
