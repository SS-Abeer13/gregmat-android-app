@echo off
echo ===================================================
echo   GregMat Vocab Pro - One-Click Build & Install
echo ===================================================

set "JAVA_HOME=F:\jdk-17.0.10+7"
set "PATH=%JAVA_HOME%\bin;C:\Users\Abeer\AppData\Local\Android\Sdk\platform-tools;F:\node-v20.11.1-win-x64;%PATH%"

set "DIRNAME=%~dp0"
if "%DIRNAME%" == "" set DIRNAME=.

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

REM Big Book icon overrides go in the BIGBOOK FLAVOR source set, never in
REM main\. Both flavors share main\assets\dist, so stamping bigbook artwork
REM there replaced the Vocab app's own in-app icons with Big Book ones.
REM Gradle merges src\bigbook\assets over src\main\assets automatically, so
REM these files reach the Big Book APK and leave the Vocab APK untouched.
if not exist "F:\GregMatAndroidApp\app\src\bigbook\assets\dist" mkdir "F:\GregMatAndroidApp\app\src\bigbook\assets\dist"
copy /Y "public\bigbook-icon-192.png" "F:\GregMatAndroidApp\app\src\bigbook\assets\dist\icon-192.png"
copy /Y "public\bigbook-icon-512.png" "F:\GregMatAndroidApp\app\src\bigbook\assets\dist\icon-512.png"
copy /Y "public\bigbook-icon-sad-192.png" "F:\GregMatAndroidApp\app\src\bigbook\assets\dist\icon-concerned-192.png"
copy /Y "public\bigbook-icon-sad-512.png" "F:\GregMatAndroidApp\app\src\bigbook\assets\dist\icon-concerned-512.png"

echo 3/3. Running Gradle Install...
cd /d "%DIRNAME%"

if "%1"=="" (
    call gradlew.bat installBigbookDebug
) else if "%1"=="installDebug" (
    call gradlew.bat installBigbookDebug
) else (
    call gradlew.bat installBigbookDebug
)

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Gradle install failed! Ensure your Android device is connected via USB with USB Debugging enabled.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ===================================================
echo SUCCESS! App compiled AND installed onto connected device!
echo ===================================================
pause
