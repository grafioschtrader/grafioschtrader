@echo off
rem E2E test orchestrator wrapper. Documentation: header of scripts/e2e-test.mjs
node "%~dp0scripts\e2e-test.mjs" %*
exit /b %ERRORLEVEL%
