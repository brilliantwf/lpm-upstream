import type { RefObject } from "react";
import { useDictationStore, type DictationPhase } from "../store/dictation";

// The dictation's phase when it is typing into the field inside `boxRef`, else
// null — so a composer can show the recorder in its own toolbar.
export function useDictationHost(boxRef: RefObject<HTMLElement | null>): DictationPhase | null {
  const phase = useDictationStore((s) => s.phase);
  const target = useDictationStore((s) => s.target?.element ?? null);
  if (phase === "idle" || !target) return null;
  return boxRef.current?.contains(target) ? phase : null;
}
