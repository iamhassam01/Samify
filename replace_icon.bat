@echo off
echo.
echo === Samify Icon Replacement Script ===
echo.
echo This script will help you replace the app icon with your custom icon.
echo.
echo STEP 1: Copy your custom icon
echo Please copy your "ic_launcher-playstore.png" file to this location:
echo %~dp0app\src\main\res\drawable\ic_launcher_playstore.png
echo.
echo STEP 2: After copying the file, press any key to continue...
pause
echo.
echo STEP 3: Building the APK with your new icon...
echo.
call gradlew assembleArm64Debug
echo.
echo STEP 4: Your new APK is ready!
echo Location: %~dp0app\build\outputs\apk\arm64\debug\app-arm64-debug.apk
echo.
echo Install it on your device to see the new icon!
echo.
pause
