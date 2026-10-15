import { createPortal } from "react-dom";
import { DICTATION_AVAILABLE } from "../../hooks/useVoiceDictation";
import { useDictationKeys } from "../../hooks/useDictationKeys";
import { useDictationStore } from "../../store/dictation";
import { DictationRecorder } from "./DictationRecorder";

// Mounted once per window. Owns the recorder's keys while this window
// dictates, and floats the recorder over the field — unless the field's
// composer shows it in its own toolbar (`data-dictation-host`).
export function DictationOverlay() {
  const phase = useDictationStore((s) => s.phase);
  const target = useDictationStore((s) => s.target?.element ?? null);
  useDictationKeys(phase);
  if (!DICTATION_AVAILABLE || phase === "idle") return null;
  const inline = target?.closest("[data-composer-box]")?.querySelector("[data-dictation-host]");
  return inline ? null : createPortal(<DictationRecorder />, document.body);
}
