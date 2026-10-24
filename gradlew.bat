@echo off
rem Gradle wrapper launcher. Downloads gradle-wrapper.jar once if it is missing (needs internet).
set "DIR=%~dp0"
set "JAR=%DIR%gradle\wrapper\gradle-wrapper.jar"
if not exist "%JAR%" (
  echo Downloading Gradle wrapper ^(one time^)...
  powershell -NoProfile -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -UseBasicParsing -Uri 'https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar' -OutFile '%JAR%'"
  if errorlevel 1 (
    if exist "%JAR%" del "%JAR%"
    echo Download failed. Open the project in Android Studio instead.
    exit /b 1
  )
)
if defined JAVA_HOME (set "JAVACMD=%JAVA_HOME%\bin\java.exe") else (set "JAVACMD=java.exe")
"%JAVACMD%" -Xmx64m -Xms64m -Dorg.gradle.appname=gradlew -classpath "%JAR%" org.gradle.wrapper.GradleWrapperMain %*
