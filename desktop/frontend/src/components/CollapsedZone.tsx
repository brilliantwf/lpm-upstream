import { useDroppable } from "@dnd-kit/core";
import { type KeyboardEvent, type MouseEvent, type ReactNode, useCallback, useEffect, useRef } from "react";
import { useHoverPopup } from "../hooks/useHoverPopup";
import type { ZoneDisplay, ZoneInfo } from "../types";
import { zoneFoldId } from "./actionsDndLayout";
import { ChevronDownIcon, ExpandIcon } from "./icons";
import { LIVE_DURING_DRAG, ZONE_SPRING_MS } from "./springLoad";
import { rowButtonClasses } from "./SplitButton";
import { ZonePopup } from "./ZonePopup";

const EXPAND_PAD: Record<ZoneDisplay, string> = { header: "px-2", footer: "px-1.5" };

// Shared with the drag preview, so a folded zone looks the same in flight.
export function collapsedZoneClasses(display: ZoneDisplay, editable: boolean) {
  const bar = rowButtonClasses(display);
  return {
    root: `inline-flex shrink-0 items-stretch select-none ${bar.height} ${bar.rounded} ${bar.border}`,
    label: `flex items-center gap-1 whitespace-nowrap font-medium ${editable ? bar.roundedL : bar.rounded} ${bar.padding}`,
    expand: `flex items-center ${bar.roundedR} ${bar.dividerBorder} ${EXPAND_PAD[display]}`,
    text: bar.text,
    hover: bar.hover,
    active: bar.active,
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
    }, ZONE_SPRING_MS);
  };
  const openedByKey = useRef(false);
  const showFromKey = () => {
    openedByKey.current = true;
    show();
  };
  const onKeyDown = (e: KeyboardEvent<HTMLButtonElement>) => {
    if (e.key !== "ArrowDown") return;
    e.preventDefault();
    showFromKey();
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
        onClick={(e) => (e.detail === 0 ? showFromKey() : show())}
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
        className={`${classes.label} transition-colors duration-100 ${popup.open ? classes.active : classes.text} ${classes.hover}`}
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
          className={`${classes.expand} transition-colors duration-100 ${classes.text} ${classes.hover}`}
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
