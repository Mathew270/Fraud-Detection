import { useEffect, useRef, useState } from "react";

// =============================================================================
// PipelineAnimation — Animated transaction flow diagram
//
// Shows how a transaction travels through the pipeline:
//   Producer → Kafka → Fraud Detector ← Redis (state lookup)
//                            ↓ (if fraud)
//                       Kafka Alerts → SSE Stream → Dashboard
//
// Design goals:
//   - Self-explanatory for newcomers (hover tooltips on each node)
//   - Accurate to the real architecture (no fake components)
//   - Great for interview/demo walkthroughs
//   - Runs automatically with no user interaction required
// =============================================================================

interface Node {
  id: string;
  label: string;
  sublabel: string;
  tooltip: string;
  x: number;
  y: number;
  color: string;
  icon: "python" | "kafka" | "python-detect" | "redis" | "spring" | "react";
}

// Packet types that can travel the pipeline
type PacketType = "transaction" | "fraud-alert";

interface Packet {
  id: number;
  type: PacketType;
  // Position along the path (0–1 per segment)
  segment: number;   // which segment index the packet is on
  progress: number;  // 0.0–1.0 progress through that segment
  active: boolean;
}

// Pipeline nodes — positioned on a 800×260 canvas
const NODES: Node[] = [
  {
    id: "producer",
    label: "Producer",
    sublabel: "Python",
    tooltip: "Generates synthetic transactions and publishes them to Kafka. Simulates real-world payment activity with configurable users, amounts, and locations.",
    x: 60, y: 100,
    color: "#3b82f6",
    icon: "python",
  },
  {
    id: "kafka-txn",
    label: "Kafka",
    sublabel: "transactions topic",
    tooltip: "Message broker that decouples producers from consumers. Transactions are durably stored and replayed from any offset — enabling scaling and fault recovery.",
    x: 210, y: 100,
    color: "#f59e0b",
    icon: "kafka",
  },
  {
    id: "detector",
    label: "Fraud Detector",
    sublabel: "Python",
    tooltip: "Consumes each transaction and applies three rules: huge amount, location anomaly (impossible travel via Haversine distance), and high-frequency window. Uses Redis for stateful lookups.",
    x: 400, y: 100,
    color: "#10b981",
    icon: "python-detect",
  },
  {
    id: "redis",
    label: "Redis",
    sublabel: "state store",
    tooltip: "Stores per-user state: last transaction location and a sliding-window sorted set of timestamps. The detector reads and writes here on every evaluation.",
    x: 400, y: 220,
    color: "#ef4444",
    icon: "redis",
  },
  {
    id: "kafka-alerts",
    label: "Kafka",
    sublabel: "fraud-alerts topic",
    tooltip: "Receives fraud alert events published by the detector. The SSE Stream service subscribes here to push alerts to the dashboard in real time.",
    x: 570, y: 100,
    color: "#f59e0b",
    icon: "kafka",
  },
  {
    id: "sse",
    label: "SSE Stream",
    sublabel: "Spring Boot",
    tooltip: "Java service that bridges Kafka and the browser. It consumes both the transactions and fraud-alerts topics and streams them over Server-Sent Events (SSE) to connected clients.",
    x: 720, y: 100,
    color: "#6366f1",
    icon: "spring",
  },
];

// Segments: [fromNodeId, toNodeId, packetType, bidirectional?]
// The animation uses these to draw moving particles on each edge
const SEGMENTS = [
  { from: "producer",     to: "kafka-txn",    type: "transaction"  as PacketType },
  { from: "kafka-txn",    to: "detector",     type: "transaction"  as PacketType },
  { from: "detector",     to: "redis",        type: "transaction"  as PacketType, bidirectional: true },
  { from: "detector",     to: "kafka-alerts", type: "fraud-alert"  as PacketType },
  { from: "kafka-alerts", to: "sse",          type: "fraud-alert"  as PacketType },
];

// Icons as inline SVG paths
function NodeIcon({ type, color }: { type: Node["icon"]; color: string }) {
  const s = { stroke: color, fill: "none", strokeWidth: 1.5 };
  switch (type) {
    case "python":
    case "python-detect":
      return (
        <svg width="22" height="22" viewBox="0 0 24 24" {...s}>
          <path d="M12 2C8.13 2 8 4.5 8 6v2h8V6c0-1.5-.13-4-4-4z" />
          <path d="M8 8H5a2 2 0 0 0-2 2v4a2 2 0 0 0 2 2h3" />
          <path d="M16 8h3a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2h-3" />
          <path d="M12 22c3.87 0 4-2.5 4-4v-2H8v2c0 1.5.13 4 4 4z" />
        </svg>
      );
    case "kafka":
      return (
        <svg width="22" height="22" viewBox="0 0 24 24" {...s}>
          <circle cx="12" cy="5" r="2.5" />
          <circle cx="5"  cy="19" r="2.5" />
          <circle cx="19" cy="19" r="2.5" />
          <line x1="12" y1="7.5" x2="5"  y2="16.5" />
          <line x1="12" y1="7.5" x2="19" y2="16.5" />
          <line x1="5"  y1="16.5" x2="19" y2="16.5" />
        </svg>
      );
    case "redis":
      return (
        <svg width="22" height="22" viewBox="0 0 24 24" {...s}>
          <ellipse cx="12" cy="6" rx="9" ry="3" />
          <path d="M3 6v4c0 1.66 4.03 3 9 3s9-1.34 9-3V6" />
          <path d="M3 10v4c0 1.66 4.03 3 9 3s9-1.34 9-3v-4" />
        </svg>
      );
    case "spring":
      return (
        <svg width="22" height="22" viewBox="0 0 24 24" {...s}>
          <path d="M12 2a10 10 0 1 0 10 10" />
          <path d="M22 2 12 12" />
          <path d="M17 2h5v5" />
        </svg>
      );
    default:
      return null;
  }
}

export function PipelineAnimation() {
  const [activeNode, setActiveNode] = useState<string | null>(null);
  const [tooltip, setTooltip] = useState<{ node: Node; x: number; y: number } | null>(null);
  const [, setTick] = useState(0);
  const packetsRef = useRef<Packet[]>([]);
  const nextIdRef = useRef(0);
  const [packets, setPackets] = useState<Packet[]>([]);
  const [fraudFlash, setFraudFlash] = useState(false);

  // Animation loop — advance packets every frame
  useEffect(() => {
    let rafId: number;
    let last = performance.now();

    const loop = (now: number) => {
      const dt = Math.min((now - last) / 1000, 0.05); // seconds, capped
      last = now;

      packetsRef.current = packetsRef.current
        .map((p) => {
          const speed = p.type === "fraud-alert" ? 0.55 : 0.45;
          let progress = p.progress + dt * speed;

          // Special pause at detector for "thinking"
          const onDetectorSegment = p.segment === 1; // kafka-txn → detector
          if (onDetectorSegment && progress > 0.85 && progress < 0.98) {
            progress = Math.min(p.progress + dt * 0.08, 0.97);
          }

          if (progress >= 1) {
            // When reaching end of segment, decide next segment or remove
            const nextSegIdx = (() => {
              if (p.segment === 1) {
                // After detector: ~25% chance of fraud path (seg 3), else redis (seg 2)
                return Math.random() < 0.25 ? 3 : 2;
              }
              if (p.segment === 2) return -1; // redis loop — remove
              if (p.segment === 3) return 4;   // detector→kafka-alerts → sse
              return -1;
            })();

            if (nextSegIdx === -1 || nextSegIdx >= SEGMENTS.length) {
              return { ...p, active: false };
            }
            // Check if this is a fraud path starting
            if (p.segment === 1 && nextSegIdx === 3) {
              setFraudFlash(true);
              setTimeout(() => setFraudFlash(false), 600);
              return { ...p, segment: nextSegIdx, progress: 0, type: "fraud-alert" as PacketType };
            }
            return { ...p, segment: nextSegIdx, progress: 0 };
          }
          return { ...p, progress };
        })
        .filter((p) => p.active);

      setPackets([...packetsRef.current]);
      setTick((t) => t + 1);
      rafId = requestAnimationFrame(loop);
    };

    rafId = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(rafId);
  }, []);

  // Spawn new packets periodically
  useEffect(() => {
    const spawn = () => {
      const newPacket: Packet = {
        id: nextIdRef.current++,
        type: "transaction",
        segment: 0,
        progress: 0,
        active: true,
      };
      packetsRef.current = [...packetsRef.current, newPacket];
    };
    spawn();
    const interval = setInterval(spawn, 1800);
    return () => clearInterval(interval);
  }, []);

  // Compute pixel position of a packet along its segment
  function packetPos(packet: Packet): { x: number; y: number } | null {
    const seg = SEGMENTS[packet.segment];
    if (!seg) return null;
    const fromNode = NODES.find((n) => n.id === seg.from)!;
    const toNode   = NODES.find((n) => n.id === seg.to)!;
    const t = packet.progress;

    // For the redis segment (bidirectional) — go down then back up
    if (seg.bidirectional) {
      if (t < 0.5) {
        const tt = t * 2;
        return { x: fromNode.x + (toNode.x - fromNode.x) * tt, y: fromNode.y + (toNode.y - fromNode.y) * tt };
      } else {
        const tt = (t - 0.5) * 2;
        return { x: toNode.x + (fromNode.x - toNode.x) * tt, y: toNode.y + (fromNode.y - toNode.y) * tt };
      }
    }

    return {
      x: fromNode.x + (toNode.x - fromNode.x) * t,
      y: fromNode.y + (toNode.y - fromNode.y) * t,
    };
  }

  function handleNodeEnter(node: Node) {
    setActiveNode(node.id);
    setTooltip({ node, x: node.x, y: node.y });
  }

  function handleNodeLeave() {
    setActiveNode(null);
    setTooltip(null);
  }

  const W = 800;
  const H = 270;

  return (
    <div className="panel pipeline-panel" id="pipeline-animation">
      <div className="panel-header">
        <h2 className="panel-title">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <polyline points="5 12 5 5 19 5 19 12" />
            <path d="M3 21h18" />
            <path d="M9 21V12h6v9" />
          </svg>
          Transaction Pipeline
        </h2>
        <span className="panel-badge pipeline-badge">Live</span>
      </div>

      <div className="pipeline-content">
        <svg
          viewBox={`0 0 ${W} ${H}`}
          className={`pipeline-svg ${fraudFlash ? "fraud-flash" : ""}`}
          preserveAspectRatio="xMidYMid meet"
        >
          {/* ---- Edge lines ---- */}
          {SEGMENTS.map((seg, i) => {
            const from = NODES.find((n) => n.id === seg.from)!;
            const to   = NODES.find((n) => n.id === seg.to)!;
            const isFraud = seg.type === "fraud-alert";
            return (
              <g key={i}>
                <line
                  x1={from.x} y1={from.y}
                  x2={to.x}   y2={to.y}
                  stroke={isFraud ? "rgba(239,68,68,0.25)" : "rgba(255,255,255,0.1)"}
                  strokeWidth={isFraud ? 1.5 : 1.5}
                  strokeDasharray={isFraud ? "5,4" : "none"}
                />
                {/* Arrow head */}
                <marker
                  id={`arrow-${i}`}
                  markerWidth="6" markerHeight="6"
                  refX="5" refY="3"
                  orient="auto"
                >
                  <path d="M0,0 L0,6 L6,3 z" fill={isFraud ? "#ef4444" : "#6b7280"} />
                </marker>
              </g>
            );
          })}

          {/* Vertical redis line label */}
          <text x="415" y="165" fill="rgba(255,255,255,0.3)" fontSize="9" fontFamily="Inter,sans-serif">
            state lookup
          </text>

          {/* ---- Nodes ---- */}
          {NODES.map((node) => {
            const isActive = activeNode === node.id;
            return (
              <g
                key={node.id}
                transform={`translate(${node.x}, ${node.y})`}
                className="pipeline-node"
                onMouseEnter={() => handleNodeEnter(node)}
                onMouseLeave={handleNodeLeave}
                style={{ cursor: "pointer" }}
              >
                {/* Glow ring when active */}
                {isActive && (
                  <circle r="34" fill={node.color} opacity="0.12" />
                )}
                {/* Background circle */}
                <circle
                  r="28"
                  fill="rgba(17,24,39,0.95)"
                  stroke={node.color}
                  strokeWidth={isActive ? 2 : 1.5}
                  opacity={isActive ? 1 : 0.85}
                />
                {/* Icon */}
                <foreignObject x="-11" y="-11" width="22" height="22" style={{ overflow: "visible" }}>
                  <NodeIcon type={node.icon} color={node.color} />
                </foreignObject>
                {/* Label */}
                <text
                  y="40"
                  textAnchor="middle"
                  fill={node.color}
                  fontSize="10"
                  fontWeight="600"
                  fontFamily="Inter,sans-serif"
                >
                  {node.label}
                </text>
                <text
                  y="52"
                  textAnchor="middle"
                  fill="rgba(156,163,175,0.8)"
                  fontSize="8.5"
                  fontFamily="Inter,sans-serif"
                >
                  {node.sublabel}
                </text>
              </g>
            );
          })}

          {/* ---- Animated packets ---- */}
          {packets.map((packet) => {
            const pos = packetPos(packet);
            if (!pos) return null;
            const isFraud = packet.type === "fraud-alert";
            return (
              <g key={packet.id}>
                {/* Glow */}
                <circle
                  cx={pos.x} cy={pos.y} r={isFraud ? 7 : 5}
                  fill={isFraud ? "rgba(239,68,68,0.3)" : "rgba(59,130,246,0.2)"}
                />
                {/* Core dot */}
                <circle
                  cx={pos.x} cy={pos.y} r={isFraud ? 4 : 3}
                  fill={isFraud ? "#ef4444" : "#3b82f6"}
                />
              </g>
            );
          })}
        </svg>

        {/* Tooltip */}
        {tooltip && (
          <div
            className="pipeline-tooltip"
            style={{
              left: `${(tooltip.node.x / W) * 100}%`,
              top: tooltip.node.y > 150 ? "auto" : "calc(100% - 80px)",
              bottom: tooltip.node.y > 150 ? "calc(100% - 60px)" : "auto",
            }}
          >
            <strong style={{ color: tooltip.node.color }}>{tooltip.node.label}</strong>
            <span className="pipeline-tooltip-sub">{tooltip.node.sublabel}</span>
            <p>{tooltip.node.tooltip}</p>
          </div>
        )}

        {/* Legend */}
        <div className="pipeline-legend">
          <span className="pipeline-legend-item">
            <span className="pipeline-legend-dot" style={{ background: "#3b82f6" }} />
            Transaction
          </span>
          <span className="pipeline-legend-item">
            <span className="pipeline-legend-dot" style={{ background: "#ef4444" }} />
            Fraud Alert (~25%)
          </span>
          <span className="pipeline-legend-hint">
            Hover any node to learn its role
          </span>
        </div>
      </div>
    </div>
  );
}
