@echo off
echo ===================================================
echo   GregMat Vocab Pro - Neo-Executive Obsidian Build
echo ===================================================

set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
set "PATH=%JAVA_HOME%\bin;F:\AndroidSDK\platform-tools;F:\node-v20.11.1-win-x64;%PATH%"

echo 1/3. Building Web Production Dist...
cd /d "C:\Users\Abeer\antigravity\GregMat-GRE-Vocab-Pro"
call npx vite build
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Vite build failed!
    pause
    exit /b %ERRORLEVEL%
)

echo 2/3. Syncing Web Assets to Android Project...
if exist "F:\GregMatAndroidApp\app\src\main\assets\dist" (
    rmdir /s /q "F:\GregMatAndroidApp\app\src\main\assets\dist"
)
xcopy /E /I /Y "dist" "F:\GregMatAndroidApp\app\src\main\assets\dist"

echo 3/3. Compiling and Installing VocabPro Obsidian App...
cd /d "F:\GregMatAndroidApp"

if "%1"=="" (
    call gradlew.bat --no-configuration-cache installObsidianDebug
) else if "%1"=="installDebug" (
    call gradlew.bat --no-configuration-cache installObsidianDebug
) else (
    call gradlew.bat --no-configuration-cache %*
)

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ------------------------------------------------------------------------
    echo If Gradle failed with "device unauthorized", please unlock your phone
    echo and tap "Allow" or "Always allow from this computer" on the USB Debugging prompt!
    echo ------------------------------------------------------------------------
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ===================================================
echo SUCCESS! GregMat VocabPro Obsidian App compiled AND installed onto device!
echo ===================================================
