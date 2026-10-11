import { useMemo } from "react";
import type { ActionsLayout } from "../types";
import { groupDropId, movedByZoneResize } from "./actionsDndLayout";
import { Remeasure } from "./Remeasure";

// A preview that moves a button between rows makes dnd-kit re-measure only
// the lists that changed. The zones' buttons, drop areas and dots slide along
// with their row and would keep their old rects, so they are measured again.
export function RowsRemeasure({ layout }: { layout: ActionsLayout }) {
  const ids = useMemo(
    () => [
      groupDropId("header"),
      groupDropId("footer"),
      ...movedByZoneResize(layout, "header"),
      ...movedByZoneResize(layout, "footer"),
    ],
    [layout],
  );
  return <Remeasure on={`${layout.header.join("\n")}\t${layout.footer.join("\n")}`} ids={ids} />;
}
