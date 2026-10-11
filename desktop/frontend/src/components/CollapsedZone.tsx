import { useDroppable } from "@dnd-kit/core";
import { type CSSProperties, type KeyboardEvent, type MouseEvent, type ReactNode, useCallback, useEffect, useRef } from "react";
import { useHoverPopup } from "../hooks/useHoverPopup";
import type { ZoneDisplay, ZoneInfo } from "../types";
import { zoneFoldId } from "./actionsDndLayout";
import { ChevronDownIcon, ExpandIcon } from "./icons";
import { SPLIT_TONE } from "./SplitButton";
import { ZonePopup } from "./ZonePopup";
import { HOVER_OPEN_MS } from "./ZoneDots";

// The bar's own button sizes: a header button is h-8, a footer one compact.
const SHAPE: Record<ZoneDisplay, { height: string; rounded: string; roundedL: string; roundedR: string; padding: string; expandPad: string }> = {
  header: { height: "h-8", rounded: "rounded-lg", roundedL: "rounded-l-lg", roundedR: "rounded-r-lg", padding: "px-3.5 text-xs", expandPad: "px-2" },
  footer: { height: "", rounded: "rounded-md", roundedL: "rounded-l-md", roundedR: "rounded-r-md", padding: "px-2.5 py-1 text-[11px]", expandPad: "px-1.5" },
};

// globals.css turns pointer events off inside the action rows mid-drag; the
// folded button takes them back so a dragged button can rest on it.
const LIVE_DURING_DRAG: CSSProperties = { pointerEvents: "auto" };

// Shared with the drag preview, so a folded zone looks the same in flight.
export function collapsedZoneClasses(display: ZoneDisplay, editable: boolean) {
  const tone = SPLIT_TONE[display];
  const shape = SHAPE[display];
  return {
    root: `inline-flex shrink-0 items-stretch select-none ${shape.height} ${shape.rounded} ${tone.border}`,
    label: `flex items-center gap-1 whitespace-nowrap font-medium ${editable ? shape.roundedL : shape.rounded} ${shape.padding}`,
    expand: `flex items-center ${shape.roundedR} ${tone.dividerBorder} ${shape.expandPad}`,
  };
}

interface CollapsedZoneProps {
  zone: ZoneInfo;
  display: ZoneDisplay;
  editable: boolean;
  dragging: boolean;
  buttonDrag: boolean;
  // The drag started in this zone, so its popup holds the lifted button.
  ownsDrag: boolean;
  onExpand: () => void;
  onContextMenu?: (e: MouseEvent<HTMLDivElement>) => void;
  // The zone's grid, shown in the popup.
  children: ReactNode;
}

export function CollapsedZone({ zone, display, editable, dragging, buttonDrag, ownsDrag, onExpand, onContextMenu, children }: CollapsedZoneProps) {
  const anchorRef = useRef<HTMLDivElement>(null);
  const labelRef = useRef<HTMLButtonElement>(null);
  // Mounted by a fold, often under the pointer that folded it.
  const popup = useHoverPopup(labelRef);
  const { show, hide, hold } = popup;
  const { setNodeRef } = useDroppable({ id: zoneFoldId(zone.name), disabled: !buttonDrag });
  const setAnchor = useCallback(
    (node: HTMLDivElement | null) => {
      anchorRef.current = node;
      setNodeRef(node);
    },
    [setNodeRef],
  );
  const tone = SPLIT_TONE[display];
  const classes = collapsedZoneClasses(display, editable);
  const springOpened = useRef(false);
  const springTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const clearSpring = useCallback(() => {
    if (springTimer.current !== null) clearTimeout(springTimer.current);
    springTimer.current = null;
  }, []);
  useEffect(() => clearSpring, [clearSpring]);
  // A drag that began in the popup, or sprang it open, keeps it up until the drop.
  const keep = popup.open && (ownsDrag || springOpened.current);
  useEffect(() => {
    if (dragging && !keep) hide();
  }, [dragging, keep, hide]);
  useEffect(() => {
    hold(dragging);
  }, [dragging, hold]);
  useEffect(() => {
    if (buttonDrag) return;
    springOpened.current = false;
    clearSpring();
  }, [buttonDrag, clearSpring]);
  const springEnter = () => {
    if (!buttonDrag || popup.open) return;
    clearSpring();
    springTimer.current = setTimeout(() => {
      springTimer.current = null;
      springOpened.current = true;
      show();
    }, HOVER_OPEN_MS);
  };
  const openedByKey = useRef(false);
  const onKeyDown = (e: KeyboardEvent<HTMLButtonElement>) => {
    if (e.key !== "ArrowDown") return;
    e.preventDefault();
    openedByKey.current = true;
    popup.show();
  };
  useEffect(() => {
    if (!popup.open) openedByKey.current = false;
  }, [popup.open]);
  return (
    <div
      ref={setAnchor}
      data-collapsed-zone=""
      onContextMenu={onContextMenu}
      className={classes.root}
    >
      <button
        ref={labelRef}
        type="button"
        aria-haspopup="true"
        aria-expanded={popup.open}
        onClick={popup.show}
        onKeyDown={onKeyDown}
        onPointerEnter={() => {
          popup.trigger.onPointerEnter();
          springEnter();
        }}
        onPointerLeave={() => {
          popup.trigger.onPointerLeave();
          clearSpring();
        }}
        style={dragging ? LIVE_DURING_DRAG : undefined}
        className={`${classes.label} transition-colors duration-100 ${popup.open ? tone.active : tone.text} ${tone.hover}`}
      >
        {zone.label}
        <ChevronDownIcon />
      </button>
      {editable && (
        <button
          type="button"
          aria-label="Expand zone"
          title="Expand zone"
          onClick={onExpand}
          onPointerDown={(e) => e.stopPropagation()}
          className={`${classes.expand} transition-colors duration-100 ${tone.text} ${tone.hover}`}
        >
          <ExpandIcon />
        </button>
      )}
      {popup.open && (!dragging || keep) && (
        <ZonePopup
          anchorRef={anchorRef}
          display={display}
          onPointerEnter={popup.panel.onPointerEnter}
          onPointerLeave={popup.panel.onPointerLeave}
          onClose={hide}
          returnFocusRef={labelRef}
          focusFirst={openedByKey.current}
        >
          {children}
        </ZonePopup>
      )}
    </div>
  );
}
