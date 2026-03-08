# =============================================================================
# Dockerfile — Python application image for the fraud detection pipeline.
#
# All three services (producer, fraud-detector, alert-consumer) share this
# image. The specific script to run is set via CMD in docker-compose.yml.
#
# Uses python:3.12-slim as the base for a small image size (~150 MB).
# =============================================================================

FROM python:3.12-slim

# Set the working directory inside the container
WORKDIR /app

# Copy and install dependencies first (Docker layer caching means this
# layer is rebuilt only when requirements.txt changes — not on every
# code edit, which speeds up builds significantly)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy the application code
COPY . .

# Default command — overridden per service in docker-compose.yml
CMD ["python", "producer.py"]
