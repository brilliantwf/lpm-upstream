import type { ZoneDisplay } from "../types";
import { CollapseIcon } from "./icons";

const TONE: Record<ZoneDisplay, string> = {
  header:
    "border-[var(--border)] bg-[var(--bg-primary)] text-[var(--text-secondary)] hover:text-[var(--text-primary)]",
  footer:
    "border-[var(--composer-border)] bg-[var(--composer-surface)] text-[var(--composer-fg-secondary)] hover:text-[var(--composer-fg)]",
};

interface ZoneCollapseButtonProps {
  display: ZoneDisplay;
  onCollapse: () => void;
}

// Shows while the pointer is over its frame (ZoneFrame's group/zone). Its
// pointerdown stays here so it can't start dragging the zone.
export function ZoneCollapseButton({ display, onCollapse }: ZoneCollapseButtonProps) {
  return (
    <button
      type="button"
      aria-label="Collapse zone"
      title="Collapse zone"
      onClick={onCollapse}
      onPointerDown={(e) => e.stopPropagation()}
      className={`absolute -top-[7px] -right-[7px] z-[4] grid h-4 w-4 cursor-pointer place-items-center rounded-full border opacity-0 shadow-[0_1px_3px_rgba(0,0,0,.25)] scale-75 transition-[opacity,transform,color] duration-150 group-hover/zone:scale-100 group-hover/zone:opacity-100 focus-visible:scale-100 focus-visible:opacity-100 ${TONE[display]}`}
    >
      <CollapseIcon />
    </button>
  );
}
