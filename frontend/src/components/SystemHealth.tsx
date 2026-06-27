import { useEffect, useState, useCallback } from "react";
import { useClusterApi } from "../hooks/useClusterApi";
import type { ClusterHealth } from "../hooks/useClusterApi";

// All services we want to display health for.
// Infrastructure services (kafka, redis, etc.) are queried the same way —
// the cluster-controller uses `docker compose ps` which works for any service.
const SERVICES = [
  { id: "kafka",             label: "Kafka",             group: "infra" },
  { id: "redis",             label: "Redis",             group: "infra" },
  { id: "producer",          label: "Producer",          group: "workers" },
  { id: "fraud-detector",    label: "Fraud Detector",    group: "workers" },
  { id: "sse-stream",        label: "SSE Stream",        group: "app" },
  { id: "cluster-controller",label: "Cluster Ctrl",      group: "app" },
  { id: "api-gateway",       label: "API Gateway",       group: "app" },
];

interface ServiceState {
  health: ClusterHealth | null;
  loading: boolean;
  error: boolean;
}

type HealthMap = Record<string, ServiceState>;

/**
 * SystemHealth component displays a live health badge for every Docker Compose
 * service in the project. It polls `GET /api/cluster/health/{service}` for each
 * service on a 10-second interval and renders a colour-coded status indicator.
 */
export function SystemHealth() {
  const { fetchHealth } = useClusterApi();

  const [healthMap, setHealthMap] = useState<HealthMap>(() =>
    Object.fromEntries(
      SERVICES.map((s) => [s.id, { health: null, loading: true, error: false }])
    )
  );

  const pollAll = useCallback(async () => {
    // Fire all health fetches in parallel for maximum freshness
    await Promise.all(
      SERVICES.map(async (svc) => {
        try {
          const h = await fetchHealth(svc.id);
          setHealthMap((prev) => ({
            ...prev,
            [svc.id]: { health: h, loading: false, error: h === null },
          }));
        } catch {
          setHealthMap((prev) => ({
            ...prev,
            [svc.id]: { health: null, loading: false, error: true },
          }));
        }
      })
    );
  }, [fetchHealth]);

  useEffect(() => {
    pollAll();
    const interval = setInterval(pollAll, 10_000);
    return () => clearInterval(interval);
  }, [pollAll]);

  const groups = [
    { key: "infra",   label: "Infrastructure" },
    { key: "workers", label: "Worker Services" },
    { key: "app",     label: "Application Layer" },
  ];

  return (
    <div className="panel system-health-panel" id="system-health">
      <div className="panel-header">
        <h2 className="panel-title">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
          </svg>
          System Health
        </h2>
        <span className="panel-badge">Live</span>
      </div>

      <div className="health-groups">
        {groups.map((group) => {
          const groupServices = SERVICES.filter((s) => s.group === group.key);
          return (
            <div key={group.key} className="health-group">
              <div className="health-group-label">{group.label}</div>
              <div className="health-service-row">
                {groupServices.map((svc) => {
                  const state = healthMap[svc.id];
                  return (
                    <ServiceCard
                      key={svc.id}
                      label={svc.label}
                      state={state}
                    />
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// ServiceCard — one tile per service
// ---------------------------------------------------------------------------
function ServiceCard({ label, state }: { label: string; state: ServiceState }) {
  const { health, loading, error } = state;

  let statusColor = "var(--text-muted)";
  let statusText  = "LOADING";
  let dotClass    = "health-dot health-dot--loading";
  let replicas: string | null = null;

  if (!loading) {
    if (error || health === null) {
      statusColor = "var(--accent-red)";
      statusText  = "UNKNOWN";
      dotClass    = "health-dot health-dot--error";
    } else if (health.status === "HEALTHY" || health.activeReplicas > 0) {
      statusColor = "var(--accent-emerald)";
      statusText  = "HEALTHY";
      dotClass    = "health-dot health-dot--healthy";
      // Only show replica count if more than 1 (i.e., it's a scalable service)
      if (health.activeReplicas > 1) {
        replicas = `${health.activeReplicas}×`;
      }
    } else if (health.status === "STOPPED" || health.activeReplicas === 0) {
      statusColor = "var(--accent-red)";
      statusText  = "STOPPED";
      dotClass    = "health-dot health-dot--error";
    } else {
      statusColor = "var(--accent-amber)";
      statusText  = health.status;
      dotClass    = "health-dot health-dot--warning";
    }
  }

  return (
    <div className="health-card" title={`${label}: ${statusText}`}>
      <div className="health-card-top">
        <span className={dotClass} />
        {replicas && (
          <span className="health-replicas">{replicas}</span>
        )}
      </div>
      <span className="health-card-label">{label}</span>
      <span className="health-card-status" style={{ color: statusColor }}>
        {statusText}
      </span>
    </div>
  );
}
