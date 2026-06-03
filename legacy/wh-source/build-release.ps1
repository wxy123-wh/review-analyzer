# ========================================
# E-Commerce Reputation Analysis System - Build Script
# ========================================

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Build Script Started" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot
$ReleaseDir = Join-Path $ProjectRoot "release"

# ========================================
# 1. Clean Release Directory
# ========================================
Write-Host "[1/5] Cleaning release directory..." -ForegroundColor Green
if (Test-Path $ReleaseDir) {
    try {
        Remove-Item -Path $ReleaseDir -Recurse -Force -ErrorAction Stop
        Write-Host "  ✓ Old release directory removed" -ForegroundColor Gray
    } catch {
        Write-Host "  ⚠ Could not remove entire release directory (files file locked?). Proceeding with overwrite..." -ForegroundColor Yellow
        # Try to remove content inside
        Get-ChildItem -Path $ReleaseDir | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
    }
}
New-Item -ItemType Directory -Path $ReleaseDir -Force | Out-Null
Write-Host "  ✓ Release directory created: $ReleaseDir" -ForegroundColor Gray
Write-Host ""

# ========================================
# 2. Build Frontend
# ========================================
Write-Host "[2/5] Building Frontend..." -ForegroundColor Green
$FrontendDir = Join-Path $ProjectRoot "frontend"

Write-Host "  → Frontend Directory: $FrontendDir" -ForegroundColor Gray

Write-Host "  → Installing dependencies..." -ForegroundColor Yellow
$installProcess = Start-Process -FilePath "npm.cmd" -ArgumentList "install" -WorkingDirectory "$FrontendDir" -NoNewWindow -PassThru -Wait
if ($installProcess.ExitCode -ne 0) {
    Write-Host "  ✗ Frontend dependency installation failed!" -ForegroundColor Red
    exit 1
}

Write-Host "  → Building frontend..." -ForegroundColor Yellow
$buildProcess = Start-Process -FilePath "npm.cmd" -ArgumentList "run","build" -WorkingDirectory "$FrontendDir" -NoNewWindow -PassThru -Wait
if ($buildProcess.ExitCode -ne 0) {
    Write-Host "  ✗ Frontend build failed!" -ForegroundColor Red
    exit 1
}
Write-Host "  ✓ Frontend build completed" -ForegroundColor Gray
Write-Host ""

# ========================================
# 3. Deploy Frontend to Backend Static Resources
# ========================================
Write-Host "[3/5] Deploying frontend to backend..." -ForegroundColor Green
$BackendStaticDir = Join-Path $ProjectRoot "backend\src\main\resources\static"
$FrontendDistDir = Join-Path $FrontendDir "dist"

# Clean old static files
if (Test-Path $BackendStaticDir) {
    Remove-Item -Path "$BackendStaticDir\*" -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host "  ✓ Old static files cleaned" -ForegroundColor Gray
} else {
    New-Item -ItemType Directory -Path $BackendStaticDir -Force | Out-Null
}

# Copy new frontend files
Copy-Item -Path "$FrontendDistDir\*" -Destination $BackendStaticDir -Recurse -Force
Write-Host "  ✓ Frontend files deployed to backend" -ForegroundColor Gray
Write-Host ""

# ========================================
# 4. Build Backend
# ========================================
Write-Host "[4/5] Building Backend..." -ForegroundColor Green
$BackendDir = Join-Path $ProjectRoot "backend"

Write-Host "  → Running Maven build..." -ForegroundColor Yellow
$mvnProcess = Start-Process -FilePath "cmd" -ArgumentList "/c","mvnw.cmd clean package -DskipTests" -WorkingDirectory "$BackendDir" -NoNewWindow -PassThru -Wait
if ($mvnProcess.ExitCode -ne 0) {
    Write-Host "  ✗ Backend build failed!" -ForegroundColor Red
    exit 1
}
Write-Host "  ✓ Backend build completed" -ForegroundColor Gray
Write-Host ""

# ========================================
# 5. Create Release Package
# ========================================
Write-Host "[5/5] Creating Release Package..." -ForegroundColor Green

# Copy Backend JAR
$JarFile = Get-ChildItem -Path (Join-Path $BackendDir "target\*.jar") | Where-Object { $_.Name -notlike "*-sources.jar" } | Select-Object -First 1
if ($JarFile) {
    Copy-Item -Path $JarFile.FullName -Destination (Join-Path $ReleaseDir "backend.jar")
    Write-Host "  ✓ Copied backend JAR: backend.jar" -ForegroundColor Gray
} else {
    Write-Host "  ✗ Backend JAR NOT found!" -ForegroundColor Red
    exit 1
}

# Copy NLP Service
$NlpServiceSrc = Join-Path $ProjectRoot "nlp_service"
$NlpServiceDest = Join-Path $ReleaseDir "nlp_service"
New-Item -ItemType Directory -Path $NlpServiceDest -Force | Out-Null
Copy-Item -Path "$NlpServiceSrc\*" -Destination $NlpServiceDest -Recurse -Force -Exclude "__pycache__","*.pyc"
Write-Host "  ✓ Copied NLP service files" -ForegroundColor Gray

# Create Start Script
$StartScript = @'
# ========================================
# Application Start Script
# ========================================

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Starting System..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$ErrorActionPreference = "Stop"
$ReleaseDir = $PSScriptRoot

# Check Python
Write-Host "[1/3] Checking Python..." -ForegroundColor Green
try {
    $PythonVersion = python --version 2>&1
    Write-Host "  ✓ $PythonVersion" -ForegroundColor Gray
} catch {
    Write-Host "  ✗ Python not found! Please install Python 3.8+" -ForegroundColor Red
    exit 1
}

# Prepare NLP Service
Write-Host ""
Write-Host "[2/3] Preparing NLP Service..." -ForegroundColor Green
$NlpDir = Join-Path $ReleaseDir "nlp_service"
Set-Location $NlpDir

if (-not (Test-Path ".venv")) {
    Write-Host "  → Creating virtual env..." -ForegroundColor Yellow
    python -m venv .venv
}

Write-Host "  → Activating virtual env..." -ForegroundColor Yellow
& .\.venv\Scripts\Activate.ps1

Write-Host "  → Installing dependencies..." -ForegroundColor Yellow
pip install -r requirements.txt -q
Write-Host "  ✓ NLP Service prepared" -ForegroundColor Gray

# Start NLP Service (Background)
Write-Host "  → Starting NLP Service..." -ForegroundColor Yellow
$PythonPath = Join-Path $NlpDir ".venv\Scripts\python.exe"
Start-Process -FilePath $PythonPath -ArgumentList "main.py" -WindowStyle Hidden -PassThru | Out-Null
Start-Sleep -Seconds 3

# Test NLP Service
try {
    $Response = Invoke-WebRequest -Uri "http://localhost:8000/health" -UseBasicParsing -TimeoutSec 5
    Write-Host "  ✓ NLP Service is running (http://localhost:8000)" -ForegroundColor Gray
} catch {
    Write-Host "  ⚠ NLP Service might have failed to start, check logs." -ForegroundColor Yellow
}

# Start Backend
Write-Host ""
Write-Host "[3/3] Starting Backend Service..." -ForegroundColor Green
Set-Location $ReleaseDir

Write-Host "  → Starting Spring Boot App..." -ForegroundColor Yellow
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "App will be available at:" -ForegroundColor Green
Write-Host "  http://localhost:8081" -ForegroundColor White
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

java -jar backend.jar
'@

$StartScript | Out-File -FilePath (Join-Path $ReleaseDir "start.ps1") -Encoding UTF8
Write-Host "  ✓ Created start script: start.ps1" -ForegroundColor Gray

# Create Stop Script
$StopScript = @'
# Stop all related processes
Write-Host "Stopping services..." -ForegroundColor Yellow

# Stop Java
Get-Process -Name "java" -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like "*backend.jar*" } | Stop-Process -Force

# Stop Python
Get-Process -Name "python" -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like "*main.py*" } | Stop-Process -Force

Write-Host "✓ Services stopped" -ForegroundColor Green
'@

$StopScript | Out-File -FilePath (Join-Path $ReleaseDir "stop.ps1") -Encoding UTF8
Write-Host "  ✓ Created stop script: stop.ps1" -ForegroundColor Gray

# Create README
$Readme = @'
# Deployment Package

## Requirements

- **Java**: 17+
- **Python**: 3.8+
- **MySQL**: 8.0+

## Database

For first time deployment:

```sql
CREATE DATABASE reputation_mvp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'repu'@'localhost' IDENTIFIED BY 'repu123456';
GRANT ALL PRIVILEGES ON reputation_mvp.* TO 'repu'@'localhost';
FLUSH PRIVILEGES;
```

## Start

Double-click `start.ps1` or run in PowerShell:

```powershell
.\start.ps1
```

> **Note**: In PowerShell, you must include `.\` before the script name.

Access: http://localhost:8081

## Stop

Double-click `stop.ps1` or run in PowerShell:

```powershell
.\stop.ps1
```

## Default Accounts

| User   | Password | Role   |
|--------|----------|--------|
| pm     | 123456   | PM     |
| market | 123456   | MARKET |
| ops    | 123456   | OPS    |

'@

$Readme | Out-File -FilePath (Join-Path $ReleaseDir "README.md") -Encoding UTF8
Write-Host "  ✓ Created README.md" -ForegroundColor Gray

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "✓ Build Successful!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Release Directory: $ReleaseDir" -ForegroundColor Cyan
