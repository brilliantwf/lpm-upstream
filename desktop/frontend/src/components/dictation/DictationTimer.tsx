import { useEffect, useState } from "react";

interface DictationTimerProps {
  startedAt: number | null;
  // Stops counting once the recording ends, holding its length.
  running: boolean;
}

export function DictationTimer({ startedAt, running }: DictationTimerProps) {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (!running) return;
    setNow(Date.now());
    const id = setInterval(() => setNow(Date.now()), 250);
    return () => clearInterval(id);
  }, [running]);

  const seconds = startedAt ? Math.max(0, Math.floor((now - startedAt) / 1000)) : 0;
  const label = `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, "0")}`;
  return (
    <span className="w-8 shrink-0 text-right text-[11.5px] tabular-nums text-[var(--dictation-muted)]">
      {label}
    </span>
  );
}
