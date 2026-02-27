@echo off
REM ID Generator Maven仓库发布脚本（Windows版本）
REM 用法: deploy.bat [release|snapshot]

setlocal enabledelayedexpansion

cd /d "%~dp0.."

echo ========================================
echo ID Generator Maven仓库发布
echo ========================================
echo.

REM 检查参数
set DEPLOY_TYPE=%1
if "%DEPLOY_TYPE%"=="" set DEPLOY_TYPE=release

if not "%DEPLOY_TYPE%"=="release" if not "%DEPLOY_TYPE%"=="snapshot" (
    echo 错误: 无效的发布类型 '%DEPLOY_TYPE%'
    echo 用法: %0 [release^|snapshot]
    exit /b 1
)

echo 发布类型: %DEPLOY_TYPE%
echo.

REM 获取当前版本
for /f "delims=" %%i in ('mvn help:evaluate -Dexpression^=project.version -q -DforceStdout') do set CURRENT_VERSION=%%i
echo 当前版本: %CURRENT_VERSION%

REM 检查版本格式
if "%DEPLOY_TYPE%"=="snapshot" (
    echo %CURRENT_VERSION% | findstr /C:"-SNAPSHOT" >nul
    if errorlevel 1 (
        echo 错误: Snapshot发布需要版本号以-SNAPSHOT结尾
        echo 当前版本: %CURRENT_VERSION%
        exit /b 1
    )
) else (
    echo %CURRENT_VERSION% | findstr /C:"-SNAPSHOT" >nul
    if not errorlevel 1 (
        echo 错误: Release发布不能使用SNAPSHOT版本
        echo 当前版本: %CURRENT_VERSION%
        exit /b 1
    )
)

echo.
echo 步骤1: 清理项目
call mvn clean
if errorlevel 1 goto :error

echo.
echo 步骤2: 运行测试
call mvn test
if errorlevel 1 goto :error

echo.
echo 步骤3: 编译打包
call mvn package -DskipTests
if errorlevel 1 goto :error

echo.
echo 步骤4: 发布到Maven仓库

if "%DEPLOY_TYPE%"=="release" (
    set /p GPG_SIGN="是否需要GPG签名? (y/n): "
    if /i "!GPG_SIGN!"=="y" (
        call mvn deploy -P release
    ) else (
        call mvn deploy
    )
) else (
    call mvn deploy
)

if errorlevel 1 goto :error

echo.
echo ========================================
echo 发布成功!
echo ========================================
echo.
echo 已发布的模块:
echo   - com.platform:id-generator-client:%CURRENT_VERSION%
echo   - com.platform:id-generator-spring-boot-starter:%CURRENT_VERSION%
echo   - com.platform:id-generator-server:%CURRENT_VERSION%
echo.
echo 其他项目可以通过以下方式使用:
echo.
echo ^<dependency^>
echo     ^<groupId^>com.platform^</groupId^>
echo     ^<artifactId^>id-generator-spring-boot-starter^</artifactId^>
echo     ^<version^>%CURRENT_VERSION%^</version^>
echo ^</dependency^>
echo.

goto :end

:error
echo.
echo 发布失败!
exit /b 1

:end
pause
