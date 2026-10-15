import { useDictationStore } from "../../store/dictation";
import { useDelayedFlag } from "../../hooks/useDelayedFlag";
import { AlertCircleIcon, XIcon } from "../icons";
import { Tooltip } from "../ui/Tooltip";
import { DictationTimer } from "./DictationTimer";
import { DictationWaveform } from "./DictationWaveform";
import "./dictation.css";

// A warm model is ready in milliseconds; only a wait longer than this earns a
// "Loading" line instead of the (still silent) trace.
const PREPARING_REVEAL_MS = 300;
const RETRYABLE = new Set(["no_speech", "too_short"]);

const ghostButton =
  "flex h-7 w-7 shrink-0 items-center justify-center rounded-lg text-[var(--dictation-muted)] transition-colors hover:bg-[var(--dictation-hover)] hover:text-[var(--dictation-ink)] focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--dictation-muted)]";

// One row of the recorder: cancel, then whatever the take is doing — loading
// the model, the level trace, the live words, or what went wrong — and the
// clock. The composer shows it in place of its tools; other fields float it
// above themselves (DictationRecorder). Colors come from `--dictation-*`, set
// by the host (`.dictation-tone-*`).
export function DictationStrip() {
  const phase = useDictationStore((s) => s.phase);
  const modelName = useDictationStore((s) => s.modelName);
  const progress = useDictationStore((s) => s.progress);
  const startedAt = useDictationStore((s) => s.startedAt);
  const partial = useDictationStore((s) => s.partial);
  const live = useDictationStore((s) => s.live);
  const error = useDictationStore((s) => s.error);
  const target = useDictationStore((s) => s.target);
  const { start, cancel } = useDictationStore.getState();

  const waiting = phase === "connecting" || phase === "preparing";
  const showWait = useDelayedFlag(waiting, PREPARING_REVEAL_MS);
  const recording = phase === "recording";
  const transcribing = phase === "transcribing";
  const announcement =
    phase === "error" ? (error?.message ?? "") : recording ? "Recording" : transcribing ? "Transcribing" : "Starting dictation";

  if (phase === "error" && error) {
    return (
      <div className="flex min-w-0 flex-1 items-center gap-2">
        <span role="status" aria-live="polite" className="sr-only">
          {announcement}
        </span>
        <span className="ml-1.5 shrink-0 text-[var(--accent-red)] [&>svg]:h-3.5 [&>svg]:w-3.5">
          <AlertCircleIcon />
        </span>
        <p title={error.message} className="line-clamp-2 min-w-0 flex-1 text-[11.5px] leading-[14px] text-[var(--dictation-ink)]">
          {error.message}
        </p>
        {RETRYABLE.has(error.code) && (
          <button
            type="button"
            onClick={() => void start(target)}
            className="shrink-0 rounded-lg px-2 py-1 text-[11.5px] font-medium text-[var(--dictation-ink)] transition-colors hover:bg-[var(--dictation-hover)] focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--dictation-muted)]"
          >
            Record again
          </button>
        )}
        <button type="button" onClick={cancel} aria-label="Dismiss" className={ghostButton}>
          <XIcon />
        </button>
      </div>
    );
  }

  return (
    <div className="flex min-w-0 flex-1 items-center gap-1.5">
      <span role="status" aria-live="polite" className="sr-only">
        {announcement}
      </span>
      <Tooltip content="Cancel · Esc" delay={400}>
        <button type="button" onClick={cancel} aria-label="Cancel dictation" className={ghostButton}>
          <XIcon />
        </button>
      </Tooltip>

      {waiting && showWait ? (
        <div className="flex min-w-0 flex-1 flex-col justify-center gap-1 pr-1">
          <div className="flex items-baseline justify-between gap-2 text-[11.5px] leading-none">
            <span className="truncate text-[var(--dictation-muted)]">
              {phase === "connecting" || !modelName ? "Starting VoiceToText…" : `Loading ${modelName}…`}
            </span>
            {progress != null && (
              <span className="shrink-0 tabular-nums text-[var(--dictation-muted)]">{Math.round(progress * 100)}%</span>
            )}
          </div>
          <div className="h-[2px] overflow-hidden rounded-full bg-[var(--dictation-track)]">
            <div
              className="dictation-progress h-full rounded-full bg-[var(--dictation-ink)]"
              data-indeterminate={progress == null || undefined}
              style={progress != null ? { width: `${Math.round(progress * 100)}%` } : undefined}
            />
          </div>
        </div>
      ) : live && partial ? (
        <div className="dictation-live flex min-w-0 flex-1 justify-end overflow-hidden whitespace-nowrap text-[12.5px] text-[var(--dictation-ink)]">
          <span className="shrink-0">{partial}</span>
        </div>
      ) : (
        <DictationWaveform frozen={transcribing} />
      )}

      {!(waiting && showWait) && <DictationTimer startedAt={startedAt} running={recording} />}
    </div>
  );
}
