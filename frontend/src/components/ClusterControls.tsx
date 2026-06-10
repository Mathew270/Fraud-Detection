// =============================================================================
// ClusterControls.tsx — Cluster scaling control panel (Phase 2 placeholder).
//
// This component provides the UI for scaling producer and fraud-detector
// workers up and down. However, these controls CANNOT function yet because:
//
//   1. The cluster_controller only speaks gRPC (port 9095)
//   2. Browsers cannot make native gRPC calls
//   3. We need the API Gateway (Phase 2) to translate REST → gRPC
//
// For now, the buttons are visually present but disabled, with a clear
// message explaining that they'll become functional after the Gateway
// is built. This is intentional — we want the UI layout to be complete
// so that when the Gateway is ready, we just flip the disabled flag.
//
// FUTURE INTEGRATION:
//   When the API Gateway is built, this component will:
//   1. Call POST /api/cluster/scale with { service: "producer", replicas: N }
//   2. Call GET  /api/cluster/health to refresh the current replica count
//   The Gateway will translate these REST calls into gRPC ScaleWorker and
//   GetClusterHealth RPCs, forwarding them to the cluster_controller.
// =============================================================================

/**
 * Cluster scaling control panel.
 *
 * Currently a placeholder with disabled controls. The visual design
 * is finalized so that enabling it later is a one-line change.
 */
export function ClusterControls() {
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
        <span className="panel-badge panel-badge-disabled">Phase 2</span>
      </div>

      <div className="cluster-content">
        {/* --- Gateway Required Notice --- */}
        <div className="gateway-notice">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="8" x2="12" y2="12" />
            <line x1="12" y1="16" x2="12.01" y2="16" />
          </svg>
          <div>
            <strong>API Gateway Required</strong>
            <p>
              Cluster scaling requires the API Gateway (Phase 2) to translate
              browser REST calls into gRPC commands for the cluster controller.
            </p>
          </div>
        </div>

        {/* --- Service Controls (disabled) --- */}
        <div className="cluster-services">
          {/* Producer scaling */}
          <div className="service-control">
            <div className="service-info">
              <span className="service-name">Producer Workers</span>
              <span className="service-replicas">? replicas</span>
            </div>
            <div className="service-buttons">
              <button className="scale-btn" disabled title="Requires API Gateway">
                −
              </button>
              <button className="scale-btn" disabled title="Requires API Gateway">
                +
              </button>
            </div>
          </div>

          {/* Fraud detector scaling */}
          <div className="service-control">
            <div className="service-info">
              <span className="service-name">Fraud Detector Workers</span>
              <span className="service-replicas">? replicas</span>
            </div>
            <div className="service-buttons">
              <button className="scale-btn" disabled title="Requires API Gateway">
                −
              </button>
              <button className="scale-btn" disabled title="Requires API Gateway">
                +
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
