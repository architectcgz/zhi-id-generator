@echo off
REM Stop ID Generator Infrastructure

cd /d "%~dp0.."

echo Stopping ID Generator infrastructure...

REM Stop services
docker-compose down

echo.
echo Infrastructure stopped successfully!
echo.
echo To remove all data volumes, run:
echo   docker-compose down -v

pause
