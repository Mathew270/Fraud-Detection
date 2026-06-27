import { useEffect, useState, useCallback } from "react";
import { useClusterApi } from "../hooks/useClusterApi";

/**
 * ClusterControls component provides the UI for:
 *   1. Scaling producer and fraud-detector workers up and down.
 *   2. Adjusting simulation parameters (num users, burst probability, speed).
 *
 * Connects to the API Gateway which translates REST → gRPC → cluster-controller.
 */
export function ClusterControls() {
  const { fetchHealth, scaleService, updateConfig, error: apiError } = useClusterApi();

  // --- Replica state ---
  const [producerReplicas, setProducerReplicas] = useState<number | null>(null);
  const [detectorReplicas, setDetectorReplicas] = useState<number | null>(null);
  const [producerStatus, setProducerStatus] = useState<string>("UNKNOWN");
  const [detectorStatus, setDetectorStatus] = useState<string>("UNKNOWN");
  const [scalingService, setScalingService] = useState<string | null>(null);
  const [localError, setLocalError] = useState<string | null>(null);

  // --- Simulation config state ---
  const [numUsers, setNumUsers] = useState<number>(20);
  const [burstProbability, setBurstProbability] = useState<number>(0.2);
  const [speedMultiplier, setSpeedMultiplier] = useState<number>(1.0);
  const [configApplying, setConfigApplying] = useState(false);
  const [configSuccess, setConfigSuccess] = useState(false);

  // --- Health polling ---
  const updateHealth = useCallback(async () => {
    const prodHealth = await fetchHealth("producer");
    if (prodHealth) {
      setProducerReplicas(prodHealth.activeReplicas);
      setProducerStatus(prodHealth.status);
    }
    const detHealth = await fetchHealth("fraud-detector");
    if (detHealth) {
      setDetectorReplicas(detHealth.activeReplicas);
      setDetectorStatus(detHealth.status);
    }
  }, [fetchHealth]);

  useEffect(() => {
    updateHealth();
    const interval = setInterval(updateHealth, 5000);
    return () => clearInterval(interval);
  }, [updateHealth]);

  // --- Scale handler ---
  const handleScale = async (service: string, currentReplicas: number | null, direction: "up" | "down") => {
    if (currentReplicas === null) return;
    const targetReplicas = direction === "up" ? currentReplicas + 1 : Math.max(0, currentReplicas - 1);

    setScalingService(service);
    setLocalError(null);

    const success = await scaleService(service, targetReplicas);
    if (success) {
      if (service === "producer") {
        setProducerReplicas(targetReplicas);
        setProducerStatus("SCALING");
      } else {
        setDetectorReplicas(targetReplicas);
        setDetectorStatus("SCALING");
      }
      setTimeout(updateHealth, 1500);
    } else {
      setLocalError(`Failed to scale ${service} to ${targetReplicas} replicas.`);
    }
    setScalingService(null);
  };

  // --- Config apply handler ---
  const handleApplyConfig = async () => {
    setConfigApplying(true);
    setConfigSuccess(false);
    setLocalError(null);

    const success = await updateConfig({ numUsers, burstProbability, speedMultiplier });
    if (success) {
      setConfigSuccess(true);
      setTimeout(() => setConfigSuccess(false), 3000);
    } else {
      setLocalError("Failed to apply simulation config.");
    }
    setConfigApplying(false);
  };

  const error = localError || apiError;

  return (
    <div className="panel cluster-panel" id="cluster-controls">
      <div className="panel-header">
        <h2 className="panel-title">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <rect x="2" y="2" width="20" height="8" rx="2" ry="2" />
            <rect x="2" y="14" width="20" height="8" rx="2" ry="2" />
            <line x1="6" y1="6" x2="6.01" y2="6" />
            <line x1="6" y1="18" x2="6.01" y2="18" />
          </svg>
          Cluster Controls
        </h2>
        <span className="panel-badge">Active</span>
      </div>

      <div className="cluster-content">
        {/* --- Error Notice --- */}
        {error && (
          <div
            className="gateway-notice"
            style={{
              background: "rgba(239, 68, 68, 0.08)",
              border: "1px solid rgba(239, 68, 68, 0.2)",
              color: "var(--accent-red)",
            }}
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="12" cy="12" r="10" />
              <line x1="12" y1="8" x2="12" y2="12" />
              <line x1="12" y1="16" x2="12.01" y2="16" />
            </svg>
            <div>
              <strong>Cluster Management Error</strong>
              <p>{error}</p>
            </div>
          </div>
        )}

        {/* --- Service Scaling --- */}
        <div className="cluster-section-label">Worker Scaling</div>
        <div className="cluster-services">
          {/* Producer scaling */}
          <div className="service-control">
            <div className="service-info">
              <span className="service-name">Producer Workers</span>
              <span className="service-replicas">
                {producerReplicas === null ? "loading..." : `${producerReplicas} replicas`}
                {producerStatus !== "UNKNOWN" && producerStatus !== "HEALTHY" && (
                  <span
                    className="service-status-badge"
                    style={{
                      marginLeft: "8px",
                      fontSize: "0.7rem",
                      padding: "2px 6px",
                      borderRadius: "4px",
                      background: "rgba(245, 158, 11, 0.15)",
                      color: "var(--accent-amber)",
                      fontWeight: 600,
                    }}
                  >
                    {producerStatus}
                  </span>
                )}
              </span>
            </div>
            <div className="service-buttons">
              <button
                className="scale-btn"
                disabled={scalingService !== null || producerReplicas === null || producerReplicas <= 0}
                onClick={() => handleScale("producer", producerReplicas, "down")}
                title="Scale Down"
              >
                −
              </button>
              <button
                className="scale-btn"
                disabled={scalingService !== null || producerReplicas === null}
                onClick={() => handleScale("producer", producerReplicas, "up")}
                title="Scale Up"
              >
                +
              </button>
            </div>
          </div>

          {/* Fraud detector scaling */}
          <div className="service-control">
            <div className="service-info">
              <span className="service-name">Fraud Detector Workers</span>
              <span className="service-replicas">
                {detectorReplicas === null ? "loading..." : `${detectorReplicas} replicas`}
                {detectorStatus !== "UNKNOWN" && detectorStatus !== "HEALTHY" && (
                  <span
                    className="service-status-badge"
                    style={{
                      marginLeft: "8px",
                      fontSize: "0.7rem",
                      padding: "2px 6px",
                      borderRadius: "4px",
                      background: "rgba(245, 158, 11, 0.15)",
                      color: "var(--accent-amber)",
                      fontWeight: 600,
                    }}
                  >
                    {detectorStatus}
                  </span>
                )}
              </span>
            </div>
            <div className="service-buttons">
              <button
                className="scale-btn"
                disabled={scalingService !== null || detectorReplicas === null || detectorReplicas <= 0}
                onClick={() => handleScale("fraud-detector", detectorReplicas, "down")}
                title="Scale Down"
              >
                −
              </button>
              <button
                className="scale-btn"
                disabled={scalingService !== null || detectorReplicas === null}
                onClick={() => handleScale("fraud-detector", detectorReplicas, "up")}
                title="Scale Up"
              >
                +
              </button>
            </div>
          </div>
        </div>

        {/* --- Simulation Config --- */}
        <div className="cluster-section-label" style={{ marginTop: "var(--space-lg)" }}>
          Simulation Parameters
        </div>
        <div className="sim-config-grid">
          {/* Num Users */}
          <div className="sim-config-item">
            <div className="sim-config-header">
              <label className="sim-config-label" htmlFor="num-users-input">
                Simulated Users
              </label>
              <span className="sim-config-value">{numUsers}</span>
            </div>
            <input
              id="num-users-input"
              type="range"
              min={1}
              max={200}
              step={1}
              value={numUsers}
              onChange={(e) => setNumUsers(Number(e.target.value))}
              className="sim-slider"
              title={`Simulated Users: ${numUsers}`}
            />
            <div className="sim-slider-labels">
              <span>1</span>
              <span>200</span>
            </div>
          </div>

          {/* Burst Probability */}
          <div className="sim-config-item">
            <div className="sim-config-header">
              <label className="sim-config-label" htmlFor="burst-prob-input">
                Burst Probability
              </label>
              <span className="sim-config-value">{(burstProbability * 100).toFixed(0)}%</span>
            </div>
            <input
              id="burst-prob-input"
              type="range"
              min={0}
              max={1}
              step={0.01}
              value={burstProbability}
              onChange={(e) => setBurstProbability(Number(e.target.value))}
              className="sim-slider"
              title={`Burst Probability: ${(burstProbability * 100).toFixed(0)}%`}
            />
            <div className="sim-slider-labels">
              <span>0%</span>
              <span>100%</span>
            </div>
          </div>

          {/* Speed Multiplier */}
          <div className="sim-config-item">
            <div className="sim-config-header">
              <label className="sim-config-label" htmlFor="speed-mult-input">
                Speed Multiplier
              </label>
              <span className="sim-config-value">{speedMultiplier.toFixed(1)}×</span>
            </div>
            <input
              id="speed-mult-input"
              type="range"
              min={0.1}
              max={10}
              step={0.1}
              value={speedMultiplier}
              onChange={(e) => setSpeedMultiplier(Number(e.target.value))}
              className="sim-slider"
              title={`Speed Multiplier: ${speedMultiplier.toFixed(1)}×`}
            />
            <div className="sim-slider-labels">
              <span>0.1×</span>
              <span>10×</span>
            </div>
          </div>
        </div>

        {/* Apply Button */}
        <button
          id="apply-sim-config-btn"
          className={`sim-apply-btn ${configApplying ? "loading" : ""} ${configSuccess ? "success" : ""}`}
          onClick={handleApplyConfig}
          disabled={configApplying}
        >
          {configApplying ? (
            <>
              <svg className="spin-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M21 12a9 9 0 1 1-6.219-8.56" />
              </svg>
              Applying…
            </>
          ) : configSuccess ? (
            <>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <polyline points="20 6 9 17 4 12" />
              </svg>
              Applied!
            </>
          ) : (
            <>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <polyline points="9 11 12 14 22 4" />
                <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" />
              </svg>
              Apply Config
            </>
          )}
        </button>
      </div>
    </div>
  );
}
