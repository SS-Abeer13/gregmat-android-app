@echo off
echo ===================================================
echo   GregMat Vocab Pro - Vocab App Build & Install
echo ===================================================

set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
set "PATH=%JAVA_HOME%\bin;F:\AndroidSDK\platform-tools;C:\Users\Abeer\AppData\Local\Android\Sdk\platform-tools;F:\node-v20.11.1-win-x64;%PATH%"

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

echo 3/3. Compiling and Installing Vocab App...
cd /d "F:\GregMatAndroidApp"

if "%1"=="" (
    call gradlew.bat --no-configuration-cache installVocabDebug
) else if "%1"=="installDebug" (
    call gradlew.bat --no-configuration-cache installVocabDebug
) else (
    call gradlew.bat --no-configuration-cache %*
)

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Gradle install failed! Ensure your Android device is connected via USB with USB Debugging enabled.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ===================================================
echo SUCCESS! GregMat Vocab Pro App compiled AND installed onto device!
echo ===================================================
pause
