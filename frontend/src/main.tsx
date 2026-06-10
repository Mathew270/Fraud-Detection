// =============================================================================
// main.tsx — Application entry point.
//
// This is the first file that runs when the browser loads the application.
// It mounts the React component tree into the DOM element with id="root"
// (defined in index.html).
//
// StrictMode is enabled to catch common React mistakes during development:
//   - Components with side effects in render
//   - Deprecated lifecycle methods
//   - Unsafe ref usage
//
// The CSS import here applies global styles to the entire application.
// =============================================================================

import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./index.css";
import App from "./App.tsx";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>
);
