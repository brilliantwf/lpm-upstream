import { useLayoutEffect, useState } from "react";
import { createPortal } from "react-dom";
import { DICTATION_AVAILABLE } from "../../hooks/useVoiceDictation";
import { useDictationKeys } from "../../hooks/useDictationKeys";
import { useDictationStore } from "../../store/dictation";
import { DictationRecorder } from "./DictationRecorder";

// How often to re-check that the field's own recorder is still on screen.
const HOST_CHECK_MS = 300;

// Mounted once per window. Owns the recorder's keys while this window
// dictates, and floats the recorder over the field — unless the field sits
// in a scope (`data-dictation-scope`: a composer, the AI popover's
// instruction) that shows the recorder itself (`data-dictation-host`), and
// that scope is on screen. A host switched away from (another tab, another
// project) or torn down mid-take hands the take back to the floating
// recorder rather than leave it recording out of sight.
export function DictationOverlay() {
  const phase = useDictationStore((s) => s.phase);
  const target = useDictationStore((s) => s.target?.element ?? null);
  useDictationKeys(phase);
  const [hosted, setHosted] = useState(() => hostedInPlace(target));
  useLayoutEffect(() => {
    if (phase === "idle") return;
    const check = () => setHosted(hostedInPlace(target));
    check();
    const id = setInterval(check, HOST_CHECK_MS);
    return () => clearInterval(id);
  }, [phase, target]);

  if (!DICTATION_AVAILABLE || phase === "idle" || hosted) return null;
  return createPortal(<DictationRecorder />, document.body);
}

function hostedInPlace(target: HTMLElement | null): boolean {
  const scope = target?.closest("[data-dictation-scope]");
  if (!scope?.isConnected || scope.getClientRects().length === 0) return false;
  return scope.hasAttribute("data-dictation-host") || scope.querySelector("[data-dictation-host]") !== null;
}
