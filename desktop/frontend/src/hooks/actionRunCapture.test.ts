import { beforeEach, describe, expect, it, vi } from "vitest";

const handlers = new Map<string, (payload: unknown) => void>();
const offs = new Map<string, ReturnType<typeof vi.fn>>();

vi.mock("../../bridge/runtime", () => ({
  EventsOnReady: vi.fn(async (name: string, callback: (payload: unknown) => void) => {
    handlers.set(name, callback);
    const off = vi.fn(() => handlers.delete(name));
    offs.set(name, off);
    return off;
  }),
}));

import { MAX_LINES, captureActionRun, cleanOutputLine } from "./actionRunCapture";

describe("captureActionRun", () => {
  beforeEach(() => {
    handlers.clear();
    offs.clear();
  });

  it("keeps output and the result that arrive before anything renders", async () => {
    const run = await captureActionRun();
    handlers.get("action-output")?.({ line: "quick-out" });
    handlers.get("action-done")?.({ success: true, error: "" });
    expect(run.snapshot()).toEqual({ lines: ["quick-out"], done: { success: true, error: "" } });
  });

  it("tells subscribers about each change and stops listening once disposed", async () => {
    const run = await captureActionRun();
    const seen = vi.fn();
    const unsubscribe = run.subscribe(seen);
    handlers.get("action-output")?.({ line: "a" });
    unsubscribe();
    handlers.get("action-output")?.({ line: "b" });
    expect(seen).toHaveBeenCalledTimes(1);
    expect(run.snapshot().lines).toEqual(["a", "b"]);
    run.dispose();
    expect(offs.get("action-output")).toHaveBeenCalled();
    expect(offs.get("action-done")).toHaveBeenCalled();
  });
});

describe("captureActionRun output limits", () => {
  beforeEach(() => {
    handlers.clear();
    offs.clear();
  });

  it("keeps only the newest lines and counts the rest", async () => {
    const run = await captureActionRun();
    for (let i = 1; i <= MAX_LINES + 5; i++) handlers.get("action-output")?.({ line: `line ${i}` });
    const snap = run.snapshot();
    expect(snap.lines).toHaveLength(MAX_LINES);
    expect(snap.lines[0]).toBe("line 6");
    expect(snap.dropped).toBe(5);
    run.dispose();
  });

  it("coalesces a burst into one notification per window", async () => {
    vi.useFakeTimers();
    try {
      const run = await captureActionRun();
      const seen = vi.fn();
      run.subscribe(seen);
      for (let i = 0; i < 1000; i++) handlers.get("action-output")?.({ line: `x${i}` });
      expect(seen).toHaveBeenCalledTimes(1);
      vi.advanceTimersByTime(60);
      expect(seen).toHaveBeenCalledTimes(2);
      expect(run.snapshot().lines).toHaveLength(1000);
      run.dispose();
    } finally {
      vi.useRealTimers();
    }
  });

  it("returns the same snapshot until something changes", async () => {
    const run = await captureActionRun();
    handlers.get("action-output")?.({ line: "a" });
    expect(run.snapshot()).toBe(run.snapshot());
    run.dispose();
  });
});

describe("cleanOutputLine", () => {
  it("drops shell-integration marks and keeps colors", () => {
    const raw =
      "\x1b]1337;RemoteHost=me@host.local\x07\x1b]1337;CurrentDir=/tmp/x\x07\x1b]1337;ShellIntegrationVersion=14;shell=zsh\x07ok 1";
    expect(cleanOutputLine(raw)).toBe("ok 1");
    expect(cleanOutputLine("\x1b[32mgreen\x1b[0m")).toBe("\x1b[32mgreen\x1b[0m");
  });

  it("drops other control sequences and stray control characters", () => {
    expect(cleanOutputLine("\x1b]0;title\x1b\\done\r")).toBe("done");
    expect(cleanOutputLine("\x1b[2K\x1b[1Gprogress")).toBe("progress");
    expect(cleanOutputLine("a\tb")).toBe("a\tb");
  });
});
