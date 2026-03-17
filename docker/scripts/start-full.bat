@echo off
REM Start Full ID Generator Environment (including server)

cd /d "%~dp0.."

echo Starting full ID Generator environment...
echo - PostgreSQL (port 5435)
echo - ID Generator Server (port 8011)
echo.

REM Check if .env exists, if not copy from example
if not exist .env (
    echo Creating .env file from .env.example...
    copy .env.example .env
)

REM Start all services including the server
docker-compose --profile full up -d

echo.
echo Waiting for services to be healthy...
timeout /t 5 /nobreak >nul
docker-compose ps

echo.
echo Full environment started successfully!
echo.
echo Services:
echo   PostgreSQL: localhost:5435
echo   ID Generator API: http://localhost:8011
echo.
echo Health check:
echo   curl http://localhost:8011/actuator/health
echo.
echo Generate Snowflake ID:
echo   curl http://localhost:8011/api/v1/id/snowflake
echo.
echo Generate Segment ID:
echo   curl http://localhost:8011/api/v1/id/segment/default
echo.
echo To view logs:
echo   docker-compose logs -f id-generator-server
echo.
echo To stop all services:
echo   docker-compose --profile full down

pause
