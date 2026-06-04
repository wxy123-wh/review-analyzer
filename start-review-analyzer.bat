@echo off
setlocal EnableExtensions EnableDelayedExpansion

cd /d "%~dp0"

set "DEFAULT_FRONTEND_PORT=5175"
set "DEFAULT_BACKEND_PORT=8080"
set "DEFAULT_NLP_PORT=8000"

echo ========================================
echo Review Analyzer one-click launcher
echo ========================================
echo Current directory: %CD%
echo.

if not exist ".env" (
  if exist ".env.example" (
  echo [1/5] .env not found, copying from .env.example...
    copy ".env.example" ".env" >nul
  ) else (
    echo [1/5] .env.example not found, skipping env bootstrap.
  )
) else (
  echo [1/5] .env found.
)

call :LoadEnv ".env"

if "%FRONTEND_PORT%"=="" set "FRONTEND_PORT=%DEFAULT_FRONTEND_PORT%"
if "%BACKEND_PORT%"=="" set "BACKEND_PORT=%DEFAULT_BACKEND_PORT%"
if "%NLP_PORT%"=="" set "NLP_PORT=%DEFAULT_NLP_PORT%"
if "%VITE_INTERNAL_ACCESS_USERNAME%"=="" set "VITE_INTERNAL_ACCESS_USERNAME=wxy"
if "%VITE_INTERNAL_ACCESS_PASSWORD%"=="" set "VITE_INTERNAL_ACCESS_PASSWORD=123456"

echo.
echo [2/5] Checking Docker Compose...
docker compose version >nul 2>nul
if errorlevel 1 (
  echo ERROR: docker compose is not available.
  echo Please start Docker Desktop and run this script again.
  pause
  exit /b 1
)

call :RemoveStoppedContainer "wh-postgres"
call :RemoveStoppedContainer "wh-redis"
call :RemoveStoppedContainer "wh-nlp-service"
call :RemoveStoppedContainer "wh-backend"
call :RemoveStoppedContainer "wh-frontend"

set "COMPOSE_SERVICES="
call :IsPortListening "%FRONTEND_PORT%"
if errorlevel 1 (
  echo [3/5] Frontend port %FRONTEND_PORT% is free. Starting the full Docker stack.
) else (
  call :IsUrlUp "http://127.0.0.1:%FRONTEND_PORT%"
  if errorlevel 1 (
    echo [3/5] WARNING: Port %FRONTEND_PORT% is in use, but it does not look like an accessible frontend.
    echo           The Docker frontend service may fail to bind this port.
  ) else (
    echo [3/5] Detected a frontend already running on %FRONTEND_PORT%. Starting backend, NLP, Postgres, and Redis only.
    set "COMPOSE_SERVICES=postgres redis nlp-service backend"
  )
)

echo.
echo [4/5] Starting Docker services. First run may download images and build containers, which can take a few minutes...
if "%COMPOSE_SERVICES%"=="" (
  docker compose up --build -d
) else (
  docker compose up --build -d %COMPOSE_SERVICES%
)
if errorlevel 1 (
  echo.
  echo Docker startup failed. Common causes: Docker Desktop is closed, or a port is already occupied.
  echo You can share the error output for further debugging.
  pause
  exit /b 1
)

echo.
echo [5/5] Waiting for health checks...
call :WaitUrl "http://127.0.0.1:%BACKEND_PORT%/api/v1/health" "Backend" 90
call :WaitUrl "http://127.0.0.1:%NLP_PORT%/health" "NLP" 60
call :WaitUrl "http://127.0.0.1:%FRONTEND_PORT%" "Frontend" 60

echo ========================================
echo Startup complete
echo ========================================
echo Frontend: http://localhost:%FRONTEND_PORT%
echo Backend health: http://localhost:%BACKEND_PORT%/api/v1/health
echo NLP health:  http://localhost:%NLP_PORT%/health
echo.
echo Login: %VITE_INTERNAL_ACCESS_USERNAME%
echo Password: %VITE_INTERNAL_ACCESS_PASSWORD%
echo.
echo Logs: docker compose logs -f
echo Stop: docker compose down
echo.
start "" "http://localhost:%FRONTEND_PORT%"
pause
exit /b 0

:LoadEnv
if not exist "%~1" exit /b 0
for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%~1") do (
  if not "%%A"=="" (
    set "%%A=%%B"
  )
)
exit /b 0

:IsPortListening
powershell -NoProfile -ExecutionPolicy Bypass -Command "if (Get-NetTCPConnection -LocalPort %~1 -State Listen -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }"
exit /b %errorlevel%

:IsUrlUp
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { $r = Invoke-WebRequest -Uri '%~1' -UseBasicParsing -TimeoutSec 3; if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 500) { exit 0 } else { exit 1 } } catch { exit 1 }"
exit /b %errorlevel%

:RemoveStoppedContainer
docker container inspect "%~1" >nul 2>nul
if errorlevel 1 exit /b 0
for /f %%S in ('docker inspect -f "{{.State.Running}}" "%~1"') do set "CONTAINER_RUNNING=%%S"
if "%CONTAINER_RUNNING%"=="true" (
  echo Existing running container found: %~1
  exit /b 0
)
echo Removing stale stopped container: %~1
docker rm "%~1" >nul
exit /b 0

:WaitUrl
set "WAIT_URL=%~1"
set "WAIT_NAME=%~2"
set "WAIT_SECONDS=%~3"
set /a "WAIT_COUNT=0"
:WaitLoop
call :IsUrlUp "%WAIT_URL%"
if not errorlevel 1 (
  echo %WAIT_NAME% is ready: %WAIT_URL%
  exit /b 0
)
set /a "WAIT_COUNT+=3"
if %WAIT_COUNT% GEQ %WAIT_SECONDS% (
  echo %WAIT_NAME% is not ready yet: %WAIT_URL%
  exit /b 1
)
timeout /t 3 /nobreak >nul
goto :WaitLoop
