import { useLayoutEffect, useRef } from "react";
import { useDndContext } from "@dnd-kit/core";

interface RemeasureProps {
  // A key that changes whenever the drop targets may have moved.
  on: string;
  ids: string[];
}

// Measures drop targets again when something moves them that dnd-kit doesn't
// watch. Its own component: useDndContext re-renders on every pointer move.
export function Remeasure({ on, ids }: RemeasureProps) {
  const { measureDroppableContainers } = useDndContext();
  const latest = useRef({ measureDroppableContainers, ids });
  latest.current = { measureDroppableContainers, ids };
  useLayoutEffect(() => {
    latest.current.measureDroppableContainers(latest.current.ids);
  }, [on]);
  return null;
}
