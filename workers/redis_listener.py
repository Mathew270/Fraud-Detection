"""
redis_listener.py — Background simulation config manager.

This module provides a thread-safe singleton object `config_state`
that holds the dynamic simulation parameters (burst probability, speed, users).

It automatically connects to Redis in a background daemon thread,
reads the starting configuration, and then listens indefinitely to the
Pub/Sub channel for live updates dispatched by the Java Cluster Controller.
"""

import json
import threading
import time

import redis

from config import settings


class SimulationConfig:
    _instance = None
    _lock = threading.Lock()

    def __new__(cls):
        """Implement Singleton pattern to ensure only one Redis thread spawns."""
        with cls._lock:
            if cls._instance is None:
                cls._instance = super(SimulationConfig, cls).__new__(cls)
                cls._instance._init()
            return cls._instance

    def _init(self):
        # Default safety fallback values (matches the Java defaults ideally)
        self.num_users = settings.num_users
        self.burst_probability = 0.25
        self.speed_multiplier = 1.0

        print(f"Connecting to Redis at {settings.redis_host}:{settings.redis_port} for dynamic config")
        self.redis_client = redis.Redis(
            host=settings.redis_host,
            port=settings.redis_port,
            decode_responses=True
        )

        # 1. Attempt to load the initial persistent state from Redis
        # (Useful if the producer restarts *after* the Java controller already set a config)
        self._load_initial_state()

        # 2. Spawn a background daemon thread to listen for instant Pub/Sub broadcasts
        self._listener_thread = threading.Thread(target=self._listen_for_updates, daemon=True)
        self._listener_thread.start()

    def _load_initial_state(self):
        try:
            val = self.redis_client.get("simulation:current-config")
            if val:
                print("Loaded persistent simulation config from Redis.")
                self._update_from_json(val)
        except redis.ConnectionError:
            print("Warning: Redis unavailable at startup. Using default config.")
        except Exception as e:
            print(f"Failed to load initial config from Redis: {e}")

    def _listen_for_updates(self):
        """Background thread loop that connects to Pub/Sub and listens forever."""
        pubsub = self.redis_client.pubsub()
        while True:
            try:
                # Subscribe to the exact channel the Java controller publishes to
                pubsub.subscribe("simulation-config")
                print("Successfully subscribed to Redis channel 'simulation-config'")

                # Listen blocks until a message arrives
                for message in pubsub.listen():
                    # Ignore subscription setup messages, only parse actual data broadcasts
                    if message["type"] == "message":
                        self._update_from_json(message["data"])

            except redis.ConnectionError:
                print("Lost connection to Redis pub/sub... retrying in 5s")
                time.sleep(5)
            except Exception as e:
                print(f"Error in Redis listener thread: {e}")
                time.sleep(5)

    def _update_from_json(self, payload: str):
        """Parse incoming JSON and update the live instance variables safely."""
        try:
            data = json.loads(payload)
            if "num_users" in data:
                self.num_users = int(data["num_users"])
            if "burst_probability" in data:
                self.burst_probability = float(data["burst_probability"])
            if "speed_multiplier" in data:
                # Prevent divide-by-zero or negative speeds from breaking sleep logic
                self.speed_multiplier = max(0.1, float(data["speed_multiplier"]))
            
            print(f"🚀 LIVE UPDATE: Simulation config adjusted -> "
                  f"Users: {self.num_users}, Burst %: {self.burst_probability}, Speed: {self.speed_multiplier}x")
        except json.JSONDecodeError:
            print(f"Received invalid JSON on simulation config channel: {payload}")


# Create the global singleton that other files will import
config_state = SimulationConfig()
