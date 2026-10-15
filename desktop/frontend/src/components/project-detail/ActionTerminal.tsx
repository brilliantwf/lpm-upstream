import { useEffect, useRef, useSyncExternalStore } from "react";
import { XIcon, CheckIcon } from "../icons";
import { SpinnerIcon, ErrorCircleIcon } from "./icons";
import { AnsiLine } from "../AnsiLine";
import { useEventListener } from "../../hooks/useEventListener";
import type { ActionRunCapture } from "../../hooks/actionRunCapture";

interface ActionTerminalProps {
  label: string;
  run: ActionRunCapture;
  onClose: () => void;
}

export function ActionTerminal({ label, run, onClose }: ActionTerminalProps) {
  const { lines, done, dropped } = useSyncExternalStore(run.subscribe, run.snapshot);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: "end" });
  }, [lines]);

  useEventListener(
    "keydown",
    (e) => {
      if (e.key === "Escape") onClose();
    },
    document,
  );

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center">
      <div className="absolute inset-0 bg-black/50" onClick={onClose} />
      <div className="relative flex w-[560px] max-h-[70vh] flex-col rounded-xl border border-[var(--border)] bg-[var(--bg-primary)] shadow-2xl overflow-hidden">
        <div className="flex items-center justify-between px-4 py-2.5 border-b border-[var(--border)] bg-[var(--bg-secondary)]">
          <div className="flex items-center gap-2">
            {!done && <SpinnerIcon />}
            {done?.success && <span className="text-[var(--accent-green)]"><CheckIcon /></span>}
            {done && !done.success && <ErrorCircleIcon />}
            <span className="text-xs font-medium text-[var(--text-primary)]">{label}</span>
            {done && (
              <span className={`text-[10px] ${done.success ? "text-[var(--accent-green)]" : "text-[var(--accent-red)]"}`}>
                {done.success ? "completed" : "failed"}
              </span>
            )}
          </div>
          <button
            onClick={onClose}
            className="rounded p-1 text-[var(--text-muted)] hover:bg-[var(--bg-hover)] hover:text-[var(--text-secondary)]"
          >
            <XIcon />
          </button>
        </div>
        <div className="flex-1 select-text overflow-y-auto bg-[var(--terminal-bg)] px-4 py-3 font-mono text-[11px] leading-relaxed text-[var(--terminal-fg)]">
          {lines.length === 0 && !done && (
            <span className="text-[var(--text-muted)]">Running...</span>
          )}
          {dropped ? (
            <div className="mb-1 text-[var(--text-muted)]">
              {dropped.toLocaleString()} earlier lines not shown
            </div>
          ) : null}
          {lines.map((line, i) => (
            <div key={(dropped ?? 0) + i} className="whitespace-pre-wrap break-all">
              {line ? <AnsiLine text={line} /> : "\u00A0"}
            </div>
          ))}
          {done?.error && (
            <div className="mt-2 text-[var(--accent-red)]">{done.error}</div>
          )}
          <div ref={bottomRef} />
        </div>
      </div>
    </div>
  );
}
