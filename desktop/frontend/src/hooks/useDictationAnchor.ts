import { useLayoutEffect, type RefObject } from "react";

const GAP = 10;
const MARGIN = 8;

// Keeps the dictation recorder floating centered just above the composer it
// types into — below it when there's no room above — following it as panes
// resize or scroll. With no field to follow it sits at the window's foot.
export function useDictationAnchor(ref: RefObject<HTMLElement | null>, target: HTMLElement | null) {
  useLayoutEffect(() => {
    let frame = 0;
    let last = "";
    const place = () => {
      const el = ref.current;
      if (el) {
        const anchor = target?.isConnected
          ? (target.closest<HTMLElement>("[data-composer-box]") ?? target)
          : null;
        const rect = anchor?.getBoundingClientRect();
        const w = el.offsetWidth;
        const h = el.offsetHeight;
        let center = window.innerWidth / 2;
        let top = window.innerHeight - h - 24;
        if (rect && rect.width > 0) {
          center = rect.left + rect.width / 2;
          top = rect.top - h - GAP;
          if (top < MARGIN) top = Math.min(rect.bottom + GAP, window.innerHeight - h - MARGIN);
        }
        const left = Math.min(Math.max(center - w / 2, MARGIN), window.innerWidth - w - MARGIN);
        const next = `translate(${Math.round(left)}px, ${Math.round(top)}px)`;
        if (next !== last) {
          el.style.transform = next;
          last = next;
        }
      }
      frame = requestAnimationFrame(place);
    };
    place();
    return () => cancelAnimationFrame(frame);
  }, [ref, target]);
}
