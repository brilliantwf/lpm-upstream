import { type RefObject, useCallback, useEffect, useMemo, useRef, useState } from "react";

export const OPEN_DELAY_MS = 120;
export const CLOSE_DELAY_MS = 280;

export interface HoverPopup {
  open: boolean;
  show: () => void;
  hide: () => void;
  // While held (a drag is on), hover neither opens nor closes the popup; it
  // only notes whether the pointer is over the trigger or the panel. Letting
  // go closes it a beat later unless the pointer is over one of them.
  hold: (on: boolean) => void;
  trigger: { onPointerEnter: () => void; onPointerLeave: () => void };
  panel: { onPointerEnter: () => void; onPointerLeave: () => void };
}

// Opens on a rest, not a pass, and closes a beat after the pointer has left
// both the trigger and the panel, so it can cross the gap between them.
// Given the trigger element, hover is ignored until the pointer is seen off it,
// so a button that appears under a resting pointer doesn't pop open.
export function useHoverPopup(waitOff?: RefObject<Element | null>): HoverPopup {
  const [open, setOpenState] = useState(false);
  const openRef = useRef(false);
  const armed = useRef(!waitOff);
  const held = useRef(false);
  const inside = useRef(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const clear = useCallback(() => {
    if (timer.current !== null) clearTimeout(timer.current);
    timer.current = null;
  }, []);
  const setOpen = useCallback((next: boolean) => {
    openRef.current = next;
    setOpenState(next);
  }, []);
  const later = useCallback(
    (ms: number, next: boolean) => {
      clear();
      timer.current = setTimeout(() => {
        timer.current = null;
        setOpen(next);
      }, ms);
    },
    [clear, setOpen],
  );
  useEffect(() => clear, [clear]);
  useEffect(() => {
    if (!waitOff) return;
    const onMove = (e: PointerEvent) => {
      if (waitOff.current?.contains(e.target as Node)) return;
      armed.current = true;
      document.removeEventListener("pointermove", onMove);
    };
    document.addEventListener("pointermove", onMove);
    return () => document.removeEventListener("pointermove", onMove);
  }, [waitOff]);
  const show = useCallback(() => {
    clear();
    armed.current = true;
    setOpen(true);
  }, [clear, setOpen]);
  const hide = useCallback(() => {
    clear();
    setOpen(false);
  }, [clear, setOpen]);
  const hold = useCallback(
    (on: boolean) => {
      held.current = on;
      if (on) clear();
      else if (openRef.current && !inside.current) later(CLOSE_DELAY_MS, false);
    },
    [clear, later],
  );
  return useMemo(() => {
    const enter = (then: () => void) => () => {
      inside.current = true;
      if (!held.current) then();
    };
    const leave = () => {
      inside.current = false;
      armed.current = true;
      if (held.current) return;
      if (openRef.current) later(CLOSE_DELAY_MS, false);
      else clear();
    };
    return {
      open,
      show,
      hide,
      hold,
      trigger: {
        onPointerEnter: enter(() => {
          if (!armed.current) return;
          if (openRef.current) clear();
          else later(OPEN_DELAY_MS, true);
        }),
        onPointerLeave: leave,
      },
      panel: { onPointerEnter: enter(clear), onPointerLeave: leave },
    };
  }, [open, show, hide, hold, clear, later]);
}
