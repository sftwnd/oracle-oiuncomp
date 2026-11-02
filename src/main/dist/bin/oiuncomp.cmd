@echo off
rem Runs oiuncomp: uncompresses files compressed by Oracle Installer.
rem Java 8 or newer is taken from JAVA_HOME, otherwise from PATH; JAVA_OPTS holds options of the JVM.
setlocal
set "OIUNCOMP_HOME=%~dp0.."
set "JAVA_EXE=java"
if defined JAVA_HOME set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
"%JAVA_EXE%" %JAVA_OPTS% -jar "%OIUNCOMP_HOME%\lib\oracle-oiuncomp.jar" %*
exit /b %ERRORLEVEL%
