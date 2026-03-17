@echo off
REM Check Health of ID Generator Services

cd /d "%~dp0.."

echo Checking ID Generator services health...
echo.

REM Check PostgreSQL
echo === PostgreSQL ===
docker-compose ps id-generator-postgres | findstr "Up" >nul
if %errorlevel% equ 0 (
    echo Status: Running
    docker exec id-generator-postgres pg_isready -U id_gen_user -d id_generator >nul 2>&1
    if %errorlevel% equ 0 (
        echo Health: Healthy
    ) else (
        echo Health: Unhealthy
    )
) else (
    echo Status: Not running
)
echo.

REM Check ID Generator Server
echo === ID Generator Server ===
docker-compose ps id-generator-server 2>nul | findstr "Up" >nul
if %errorlevel% equ 0 (
    echo Status: Running
    curl -s http://localhost:8011/actuator/health >nul 2>&1
    if %errorlevel% equ 0 (
        echo Health: Check http://localhost:8011/actuator/health
    ) else (
        echo Health: Cannot connect
    )
) else (
    echo Status: Not running (use --profile full to start^)
)
echo.

echo === Docker Compose Status ===
docker-compose ps

pause
