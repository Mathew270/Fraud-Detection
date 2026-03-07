@echo off
REM =========================================================================
REM stop.bat — Shut down the entire Fraud Detection pipeline
REM   1. Terminate the three Python service processes
REM   2. Stop and remove Docker Compose containers
REM =========================================================================

echo ============================================================
echo  Stopping Fraud Detection Pipeline
echo ============================================================

REM --- 1. Kill Python services ---
echo.
echo [1/2] Stopping Python services...

REM Kill any python processes running our scripts
taskkill /FI "WINDOWTITLE eq Fraud Detector*" /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq Producer*"       /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq Alert Consumer*" /F >nul 2>&1

echo        Python services stopped.

REM --- 2. Tear down Docker containers ---
echo.
echo [2/2] Stopping Docker Compose services...
docker compose down

echo.
echo ============================================================
echo  Pipeline stopped.
echo ============================================================
