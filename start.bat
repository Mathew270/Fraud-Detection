@echo off
REM =========================================================================
REM start.bat — Launch the entire Fraud Detection pipeline
REM   1. Start infrastructure (Kafka, Redis, UIs) via Docker Compose
REM   2. Wait for services to become healthy
REM   3. Launch the three Python services in separate windows
REM =========================================================================

echo ============================================================
echo  Starting Fraud Detection Pipeline
echo ============================================================

REM --- 1. Start infrastructure containers ---
echo.
echo [1/3] Starting Docker Compose services...
docker compose up -d
if %ERRORLEVEL% neq 0 (
    echo ERROR: docker compose failed. Is Docker running?
    pause
    exit /b 1
)

REM --- 2. Wait for Kafka and Redis to be healthy ---
echo.
echo [2/3] Waiting for Kafka and Redis to become healthy...

:wait_kafka
docker inspect --format="{{.State.Health.Status}}" kafka 2>nul | findstr "healthy" >nul
if %ERRORLEVEL% neq 0 (
    echo        Kafka not ready yet, retrying in 5s...
    timeout /t 5 /nobreak >nul
    goto wait_kafka
)
echo        Kafka is healthy.

:wait_redis
docker inspect --format="{{.State.Health.Status}}" redis 2>nul | findstr "healthy" >nul
if %ERRORLEVEL% neq 0 (
    echo        Redis not ready yet, retrying in 5s...
    timeout /t 5 /nobreak >nul
    goto wait_redis
)
echo        Redis is healthy.

REM --- 3. Launch Python services in separate windows ---
echo.
echo [3/3] Launching Python services...

start "Fraud Detector"    cmd /k "cd /d %~dp0app && %~dp0.venv\Scripts\python.exe fraud_detector.py"
start "Producer"          cmd /k "cd /d %~dp0app && %~dp0.venv\Scripts\python.exe producer.py"
start "Alert Consumer"    cmd /k "cd /d %~dp0app && %~dp0.venv\Scripts\python.exe alert_consumer.py"

echo.
echo ============================================================
echo  Pipeline is running!
echo  - Fraud Detector, Producer, Alert Consumer in separate windows
echo  - Kafka UI:          http://localhost:8080
echo  - RedisInsight:      http://localhost:8001
echo  - Redis Commander:   http://localhost:8082
echo ============================================================
