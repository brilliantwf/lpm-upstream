import type { CSSProperties, ReactNode } from "react";
import type { ZoneDisplay } from "../types";

// For what renders outside the terminal pane: a footer button or zone brings
// the pane's colours along.
export function InBar({ display, footerStyle, children }: { display: ZoneDisplay; footerStyle?: CSSProperties; children: ReactNode }) {
  if (display !== "footer") return <>{children}</>;
  return (
    <div className="composer-terminal-surface" style={footerStyle}>
      {children}
    </div>
  );
}
