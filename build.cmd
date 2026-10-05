@echo off
rem Runs the Gradle wrapper with Android Studio's bundled JDK and the default SDK location.
rem From WSL: cmd.exe /c build.cmd installDebug
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
if not defined ANDROID_HOME set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
call "%~dp0gradlew.bat" %*
