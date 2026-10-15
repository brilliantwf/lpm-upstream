import { useCallback, useState } from "react";
import { toast } from "sonner";
import { VoiceToTextAvailable } from "../../bridge/commands";
import { isMac } from "../platform";
import { captureDictationTarget } from "../dictation/insert";
import { ACTIVE_PHASES, useDictationStore } from "../store/dictation";

// VoiceToText is a macOS app; elsewhere the composer offers no dictation.
export const DICTATION_AVAILABLE = isMac;

// Shared dictation control: starts lpm's recorder on VoiceToText — the text
// goes into the field that has focus now, so callers keep focus on their input
// while toggling — or stops it, or surfaces the install prompt when the app
// isn't there.
export function useVoiceDictation() {
  const [installOpen, setInstallOpen] = useState(false);
  const phase = useDictationStore((s) => s.phase);
  const active = ACTIVE_PHASES.has(phase);

  const toggle = useCallback(async () => {
    const { phase, start, stop, cancel } = useDictationStore.getState();
    if (phase === "recording") return stop();
    if (phase === "transcribing") return;
    if (ACTIVE_PHASES.has(phase)) return cancel();
    const target = captureDictationTarget();
    try {
      if (!(await VoiceToTextAvailable())) {
        setInstallOpen(true);
        return;
      }
      await start(target);
    } catch (err) {
      toast.error(`Voice dictation failed: ${err}`);
    }
  }, []);

  return { toggle, phase, active, installOpen, setInstallOpen };
}
