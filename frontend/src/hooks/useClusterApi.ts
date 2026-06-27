// =============================================================================
// useClusterApi.ts — Custom React hook for calling Cluster Controller API.
//
// Translates frontend administrative actions (scaling services, fetching health)
// into REST requests sent to the API Gateway on port 8090 (or proxied in dev).
//
// The API Gateway then translates these REST endpoints into gRPC calls and
// forwards them to the cluster-controller service.
// =============================================================================

import { useState, useCallback } from "react";

/** Response format for a service health request */
export interface ClusterHealth {
  serviceName: string;
  activeReplicas: number;
  status: string;
}

/** Hook to wrap cluster operations and manage loading/error state */
export function useClusterApi() {
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  /**
   * Fetches the current replica count and status for a service.
   * GET /api/cluster/health/{service}
   */
  const fetchHealth = useCallback(async (service: string): Promise<ClusterHealth | null> => {
    setLoading(true);
    setError(null);
    try {
      const response = await fetch(`/api/cluster/health/${service}`);
      if (!response.ok) {
        throw new Error(`Failed to fetch health for service ${service}: ${response.statusText}`);
      }
      const data: ClusterHealth = await response.json();
      return data;
    } catch (err: any) {
      const errMsg = err.message || "An error occurred fetching health";
      setError(errMsg);
      console.error(errMsg);
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  /**
   * Scales a service to the target number of replicas.
   * POST /api/cluster/scale
   */
  const scaleService = useCallback(async (service: string, replicas: number): Promise<boolean> => {
    setLoading(true);
    setError(null);
    try {
      const response = await fetch(`/api/cluster/scale`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ service, replicas }),
      });
      if (!response.ok) {
        throw new Error(`Failed to scale service ${service}: ${response.statusText}`);
      }
      const data = await response.json();
      return data.success;
    } catch (err: any) {
      const errMsg = err.message || "An error occurred scaling service";
      setError(errMsg);
      console.error(errMsg);
      return false;
    } finally {
      setLoading(false);
    }
  }, []);

  /**
   * Updates the simulation config parameters.
   * POST /api/cluster/config
   */
  const updateConfig = useCallback(async (config: {
    numUsers?: number;
    burstProbability?: number;
    speedMultiplier?: number;
  }): Promise<boolean> => {
    setLoading(true);
    setError(null);
    try {
      const response = await fetch(`/api/cluster/config`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(config),
      });
      if (!response.ok) {
        throw new Error(`Failed to update simulation configuration: ${response.statusText}`);
      }
      const data = await response.json();
      return data.success;
    } catch (err: any) {
      const errMsg = err.message || "An error occurred updating configuration";
      setError(errMsg);
      console.error(errMsg);
      return false;
    } finally {
      setLoading(false);
    }
  }, []);

  return {
    fetchHealth,
    scaleService,
    updateConfig,
    loading,
    error,
  };
}
