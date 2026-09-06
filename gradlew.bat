@ECHO OFF
SETLOCAL
SET APP_DIR=%~dp0
CD /D "%APP_DIR%"
java -cp "%APP_DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
EXIT /B %ERRORLEVEL%
