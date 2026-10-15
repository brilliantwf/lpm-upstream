import { EventsOnReady } from "../../bridge/runtime";

export interface ActionDone {
  success: boolean;
  error?: string;
}

export interface ActionRunSnapshot {
  lines: string[];
  done: ActionDone | null;
  // Lines dropped from the front to keep the newest MAX_LINES.
  dropped?: number;
}

export interface ActionRunCapture {
  snapshot: () => ActionRunSnapshot;
  subscribe: (onChange: () => void) => () => void;
  dispose: () => void;
}

// A run can print far more than the output modal can show: rebuilding the
// whole list per line froze the webview for minutes on 50k lines. The modal
// keeps the newest lines and redraws at most every NOTIFY_MS.
export const MAX_LINES = 2000;
const NOTIFY_MS = 50;

const ESC = "\x1b";
// Everything a line can carry besides SGR colors: OSC sequences (titles, the
// marks iTerm2's shell integration prints at login, links), other CSI
// sequences, and stray control characters such as a CRLF's \r.
const NON_SGR = new RegExp(
  `${ESC}\\][^\\x07${ESC}]*(?:\\x07|${ESC}\\\\)|${ESC}\\[[0-?]*[ -/]*[@-ln-~]|[\\x00-\\x08\\x0b-\\x1a\\x1c-\\x1f\\x7f]`,
  "g",
);

export function cleanOutputLine(line: string): string {
  return line.replace(NON_SGR, "");
}

// An inline action streams its output as global events straight from the
// process, and a fast one is finished before RunAction even returns. Listening
// only once the output modal mounts loses those events, leaving the modal on
// "Running..." with no output, so the capture attaches before the run starts.
export async function captureActionRun(): Promise<ActionRunCapture> {
  const lines: string[] = [];
  let dropped = 0;
  let done: ActionDone | null = null;
  let snap: ActionRunSnapshot = { lines: [], done: null };
  let stale = false;
  const watchers = new Set<() => void>();

  let lastNotify = 0;
  let timer: ReturnType<typeof setTimeout> | null = null;
  const notifyNow = () => {
    if (timer) clearTimeout(timer);
    timer = null;
    lastNotify = Date.now();
    watchers.forEach((w) => w());
  };
  const notify = () => {
    stale = true;
    if (timer) return;
    const wait = NOTIFY_MS - (Date.now() - lastNotify);
    if (wait <= 0) notifyNow();
    else timer = setTimeout(notifyNow, wait);
  };

  const offs = await Promise.all([
    EventsOnReady("action-output", (data: { line: string }) => {
      lines.push(cleanOutputLine(data.line));
      if (lines.length > MAX_LINES) {
        const over = lines.length - MAX_LINES;
        lines.splice(0, over);
        dropped += over;
      }
      notify();
    }),
    EventsOnReady("action-done", (data: ActionDone) => {
      done = data;
      stale = true;
      notifyNow();
    }),
  ]);
  return {
    snapshot: () => {
      if (stale) {
        stale = false;
        snap = { lines: lines.slice(), done, ...(dropped ? { dropped } : {}) };
      }
      return snap;
    },
    subscribe: (onChange) => {
      watchers.add(onChange);
      return () => watchers.delete(onChange);
    },
    dispose: () => {
      if (timer) clearTimeout(timer);
      offs.forEach((off) => off());
    },
  };
}
