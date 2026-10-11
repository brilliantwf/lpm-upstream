import { type ReactNode, type RefObject, useEffect, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useTerminalTheme } from "../hooks/useTerminalTheme";
import type { ZoneDisplay } from "../types";
import { InBar } from "./InBar";

const GAP_PX = 6;
const VIEWPORT_MARGIN_PX = 8;

interface PopupPlacement {
  left: number;
  top?: number;
  bottom?: number;
}

// From the button's left edge, or its right edge when that would run off the
// window. Worked out once on opening, so the popup holds still.
export function placePopup(
  anchor: DOMRect,
  width: number,
  side: "above" | "below",
  viewport: { width: number; height: number },
): PopupPlacement {
  const fits = anchor.left + width <= viewport.width - VIEWPORT_MARGIN_PX;
  const left = Math.max(VIEWPORT_MARGIN_PX, fits ? anchor.left : anchor.right - width);
  return side === "above"
    ? { left, bottom: viewport.height - anchor.top + GAP_PX }
    : { left, top: anchor.bottom + GAP_PX };
}

// Over the header, footer and terminal (up to z-40) but under dialogs (z-50+),
// so a confirm or inputs action run from the popup opens on top of it.
const POPUP_Z = "z-[45]";

const SURFACE: Record<ZoneDisplay, string> = {
  header: "bg-[var(--bg-primary)]",
  footer: "bg-[var(--composer-surface)]",
};

// An invisible strip over the gap, so crossing it never leaves the popup.
const BRIDGE: Record<ZoneDisplay, string> = {
  header: "before:-top-[9px]",
  footer: "before:-bottom-[9px]",
};

interface ZonePopupProps {
  anchorRef: RefObject<HTMLElement | null>;
  display: ZoneDisplay;
  onPointerEnter: () => void;
  onPointerLeave: () => void;
  onClose: () => void;
  // Escape sends focus here when it was in the popup.
  returnFocusRef: RefObject<HTMLElement | null>;
  focusFirst?: boolean;
  children: ReactNode;
}

export function ZonePopup({
  anchorRef,
  display,
  onPointerEnter,
  onPointerLeave,
  onClose,
  returnFocusRef,
  focusFirst,
  children,
}: ZonePopupProps) {
  const panelRef = useRef<HTMLDivElement>(null);
  const [placement, setPlacement] = useState<PopupPlacement | null>(null);
  const { themeStyle } = useTerminalTheme();
  useLayoutEffect(() => {
    const anchor = anchorRef.current?.getBoundingClientRect();
    const panel = panelRef.current;
    if (!anchor || !panel) return;
    const side = display === "footer" ? "above" : "below";
    setPlacement(placePopup(anchor, panel.offsetWidth, side, { width: window.innerWidth, height: window.innerHeight }));
    if (focusFirst) panel.querySelector<HTMLButtonElement>("button:not(:disabled)")?.focus({ preventScroll: true });
  }, [anchorRef, display, focusFirst]);
  useEffect(() => {
    const onMouseDown = (e: globalThis.MouseEvent) => {
      const target = e.target as Element;
      if (anchorRef.current?.contains(target) || panelRef.current?.contains(target)) return;
      // An action's dropdown or right-click menu, portaled out of the panel.
      if (target.closest?.("[data-actions-menu], [data-context-menu]")) return;
      onClose();
    };
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key !== "Escape") return;
      // Mid-drag, Escape cancels the drag, through dnd-kit's own listener.
      if (document.body.hasAttribute("data-lpm-drag")) return;
      const focusInside = panelRef.current?.contains(document.activeElement);
      onClose();
      if (focusInside) returnFocusRef.current?.focus();
    };
    document.addEventListener("mousedown", onMouseDown);
    document.addEventListener("keydown", onKeyDown);
    window.addEventListener("resize", onClose);
    return () => {
      document.removeEventListener("mousedown", onMouseDown);
      document.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("resize", onClose);
    };
  }, [anchorRef, onClose, returnFocusRef]);
  return createPortal(
    <InBar display={display} footerStyle={themeStyle}>
      <div
        ref={panelRef}
        data-zone-popup=""
        onPointerDown={(e) => e.stopPropagation()}
        onPointerEnter={onPointerEnter}
        onPointerLeave={onPointerLeave}
        style={placement ? { position: "fixed", ...placement } : { position: "fixed", left: 0, top: 0, visibility: "hidden" }}
        className={`${POPUP_Z} rounded-[10px] shadow-xl before:absolute before:inset-x-[-10px] before:h-[9px] before:content-[''] ${SURFACE[display]} ${BRIDGE[display]}`}
      >
        {children}
      </div>
    </InBar>,
    document.body,
  );
}
