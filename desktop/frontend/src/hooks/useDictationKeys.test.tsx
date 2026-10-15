// @vitest-environment happy-dom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const commands = vi.hoisted(() => ({
  VoiceToTextStart: vi.fn(),
  VoiceToTextStop: vi.fn(),
  VoiceToTextCancel: vi.fn(),
  VoiceToTextToggle: vi.fn(),
  SetClipboardText: vi.fn(),
}));

vi.mock("../../bridge/commands", () => commands);
vi.mock("../../bridge/runtime", () => ({ EventsOnReady: async () => () => {} }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

import { useDictationKeys } from "./useDictationKeys";
import { useDictationStore } from "../store/dictation";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

function Keys() {
  useDictationKeys(useDictationStore((s) => s.phase));
  return null;
}

let root: Root;
let composerKeys: string[];
const composer = document.createElement("div");
composer.addEventListener("keydown", (e) => composerKeys.push(e.key));

function press(key: string) {
  const event = new KeyboardEvent("keydown", { key, bubbles: true, cancelable: true });
  act(() => {
    composer.dispatchEvent(event);
  });
  return event;
}

beforeEach(() => {
  for (const fn of Object.values(commands)) fn.mockReset().mockResolvedValue(undefined);
  composerKeys = [];
  document.body.append(composer);
  root = createRoot(document.createElement("div"));
  act(() => root.render(<Keys />));
});

afterEach(() => {
  act(() => root.unmount());
  useDictationStore.setState({ phase: "idle", error: null });
});

describe("useDictationKeys", () => {
  it("Return finishes a recording instead of reaching the composer", () => {
    act(() => useDictationStore.setState({ phase: "recording" }));
    const event = press("Enter");
    expect(event.defaultPrevented).toBe(true);
    expect(commands.VoiceToTextStop).toHaveBeenCalledOnce();
    expect(composerKeys).toEqual([]);
  });

  it("Esc cancels", () => {
    act(() => useDictationStore.setState({ phase: "recording" }));
    press("Escape");
    expect(commands.VoiceToTextCancel).toHaveBeenCalledOnce();
    expect(useDictationStore.getState().phase).toBe("idle");
    expect(composerKeys).toEqual([]);
  });

  it("swallows Return while transcribing so a half-done prompt isn't sent", () => {
    act(() => useDictationStore.setState({ phase: "transcribing" }));
    press("Enter");
    expect(commands.VoiceToTextStop).not.toHaveBeenCalled();
    expect(composerKeys).toEqual([]);
  });

  it("lets Return through under an error notice, and Esc dismisses it", () => {
    act(() => useDictationStore.setState({ phase: "error", error: { code: "no_speech", message: "No speech." } }));
    press("Enter");
    expect(composerKeys).toEqual(["Enter"]);
    press("Escape");
    expect(useDictationStore.getState().phase).toBe("idle");
  });

  it("leaves every key alone when not dictating", () => {
    press("Enter");
    press("Escape");
    expect(composerKeys).toEqual(["Enter", "Escape"]);
  });
});
