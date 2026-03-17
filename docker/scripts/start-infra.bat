@echo off
REM Start ID Generator Infrastructure
REM This script starts PostgreSQL for local development

cd /d "%~dp0.."

echo Starting ID Generator infrastructure...
echo - PostgreSQL (port 5435)
echo.

REM Check if .env exists, if not copy from example
if not exist .env (
    echo Creating .env file from .env.example...
    copy .env.example .env
)

REM Start services
docker-compose up -d id-generator-postgres

echo.
echo Waiting for services to be healthy...
docker-compose ps

echo.
echo Infrastructure started successfully!
echo.
echo PostgreSQL:
echo   Host: localhost
echo   Port: 5435
echo   Database: id_generator
echo   User: id_gen_user
echo   Password: id_gen_password
echo.
echo To view logs:
echo   docker-compose logs -f id-generator-postgres
echo.
echo To stop infrastructure:
echo   docker-compose down

pause
