import { useRef, type MouseEvent } from "react";
import { useDictationStore } from "../../store/dictation";
import { useDictationAnchor } from "../../hooks/useDictationAnchor";
import { CheckIcon } from "../icons";
import { Tooltip } from "../ui/Tooltip";
import { DictationSpinner } from "./DictationSpinner";
import { DictationStrip } from "./DictationStrip";

// The recorder for a field without a composer toolbar to hold it — the AI
// popover's instruction, a plain input — floating just above that field.
export function DictationRecorder() {
  const phase = useDictationStore((s) => s.phase);
  const target = useDictationStore((s) => s.target);
  const { stop } = useDictationStore.getState();

  const wrapRef = useRef<HTMLDivElement>(null);
  useDictationAnchor(wrapRef, target?.element ?? null);

  const recording = phase === "recording";
  const transcribing = phase === "transcribing";
  // Clicks here must leave the caret in the field the text is going into.
  const keepFocus = (e: MouseEvent) => e.preventDefault();

  return (
    <div ref={wrapRef} className="pointer-events-none fixed left-0 top-0 z-[95]">
      <div
        onMouseDown={keepFocus}
        className="dictation-capsule dictation-tone-app pointer-events-auto flex h-9 w-[300px] max-w-[calc(100vw-16px)] items-center gap-1 rounded-xl border border-[var(--border)] bg-[var(--bg-primary)] px-1 shadow-[0_10px_28px_-12px_rgba(0,0,0,0.45),0_2px_6px_-2px_rgba(0,0,0,0.12)]"
      >
        <DictationStrip />
        {phase !== "error" && (
          <Tooltip content={transcribing ? "Transcribing…" : "Insert text · Return"} delay={400}>
            <button
              type="button"
              onClick={stop}
              disabled={!recording}
              aria-label={transcribing ? "Transcribing" : "Stop and insert text"}
              className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-[var(--text-primary)] text-[var(--bg-primary)] transition-[opacity,transform] hover:opacity-85 focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--text-muted)] active:scale-95 disabled:cursor-default disabled:active:scale-100 ${
                recording || transcribing ? "" : "opacity-30"
              }`}
            >
              {transcribing ? (
                <DictationSpinner />
              ) : (
                <span className="[&>svg]:h-3.5 [&>svg]:w-3.5">
                  <CheckIcon />
                </span>
              )}
            </button>
          </Tooltip>
        )}
      </div>
    </div>
  );
}
