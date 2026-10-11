import { createContext, useContext } from "react";

// Holding a dragged item over a spring-load target this long opens its menu
// (top-level dropdown or drill submenu) so the user can drop deeper.
export const SPRING_LOAD_MS = 700;

// A dragged button resting this long on a zone's dots or folded button opens
// that layer or the zone's popup.
export const ZONE_SPRING_MS = 500;

// globals.css turns pointer events off inside the action rows mid-drag; a
// spring target among them takes them back so a dragged button can rest on it.
export const LIVE_DURING_DRAG = { pointerEvents: "auto" } as const;

// True while a drag is dwelling over the surrounding sortable item, so a menu
// trigger nested inside it can spring its dropdown open.
export const SpringOverContext = createContext(false);

export function useSpringOver(): boolean {
  return useContext(SpringOverContext);
}
