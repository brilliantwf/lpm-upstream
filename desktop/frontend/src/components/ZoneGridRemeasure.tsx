import { useLayoutEffect, useRef } from "react";
import { useDndContext } from "@dnd-kit/core";

interface ZoneGridRemeasureProps {
  // The open layer's buttons, in order.
  buttons: string[];
  // The drop targets the grid's size moves: its drop area and dots.
  ids: string[];
}

// A popup holds its left edge while a preview takes a button out of its grid,
// so its drop area shrinks and its dots shift without any row changing, and
// dnd-kit would keep their old rects. Its own component: useDndContext
// re-renders on every pointer move.
export function ZoneGridRemeasure({ buttons, ids }: ZoneGridRemeasureProps) {
  const { measureDroppableContainers } = useDndContext();
  const latest = useRef({ measureDroppableContainers, ids });
  latest.current = { measureDroppableContainers, ids };
  const key = buttons.join("\n");
  useLayoutEffect(() => {
    latest.current.measureDroppableContainers(latest.current.ids);
  }, [key]);
  return null;
}
