// @vitest-environment happy-dom
import { act, useRef } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { CLOSE_DELAY_MS, type HoverPopup, OPEN_DELAY_MS, useHoverPopup } from "./useHoverPopup";

let popup: HoverPopup;
function Harness({ waitOff }: { waitOff?: boolean }) {
  const ref = useRef<HTMLButtonElement>(null);
  popup = useHoverPopup(waitOff ? ref : undefined);
  return <button ref={ref} type="button" data-trigger="" />;
}

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  vi.useFakeTimers();
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  vi.useRealTimers();
});

const mount = (waitOff = false) => act(() => root.render(<Harness waitOff={waitOff} />));
const moveOver = (el: Element) =>
  act(() => {
    el.dispatchEvent(new MouseEvent("pointermove", { bubbles: true }));
  });
const trigger = () => container.querySelector("[data-trigger]")!;
const wait = (ms: number) => act(() => vi.advanceTimersByTime(ms));
const call = (fn: () => void) => act(fn);

function openIt() {
  call(() => popup.trigger.onPointerEnter());
  wait(OPEN_DELAY_MS);
}

describe("useHoverPopup", () => {
  it("opens after the pointer rests on the trigger", () => {
    mount();
    call(() => popup.trigger.onPointerEnter());
    wait(OPEN_DELAY_MS - 1);
    expect(popup.open).toBe(false);
    wait(1);
    expect(popup.open).toBe(true);
  });

  it("stays shut when the pointer only passes over", () => {
    mount();
    call(() => popup.trigger.onPointerEnter());
    wait(60);
    call(() => popup.trigger.onPointerLeave());
    wait(1000);
    expect(popup.open).toBe(false);
  });

  it("closes a beat after the pointer leaves", () => {
    mount();
    openIt();
    call(() => popup.trigger.onPointerLeave());
    wait(CLOSE_DELAY_MS - 1);
    expect(popup.open).toBe(true);
    wait(1);
    expect(popup.open).toBe(false);
  });

  it("stays open when the pointer crosses into the panel", () => {
    mount();
    openIt();
    call(() => popup.trigger.onPointerLeave());
    wait(100);
    call(() => popup.panel.onPointerEnter());
    wait(1000);
    expect(popup.open).toBe(true);
    call(() => popup.panel.onPointerLeave());
    wait(CLOSE_DELAY_MS);
    expect(popup.open).toBe(false);
  });

  it("given the trigger, ignores a rest on it until the pointer has left it", () => {
    mount(true);
    call(() => popup.trigger.onPointerEnter());
    wait(500);
    expect(popup.open).toBe(false);
    call(() => popup.trigger.onPointerLeave());
    openIt();
    expect(popup.open).toBe(true);
  });

  it("given the trigger, keeps ignoring it while the pointer moves on it", () => {
    mount(true);
    moveOver(trigger());
    openIt();
    expect(popup.open).toBe(false);
  });

  it("given the trigger, opens on the first rest once the pointer has moved anywhere off it", () => {
    mount(true);
    moveOver(document.body);
    openIt();
    expect(popup.open).toBe(true);
  });

  it("opens and closes at once on show and hide", () => {
    mount();
    call(() => popup.show());
    expect(popup.open).toBe(true);
    call(() => popup.hide());
    expect(popup.open).toBe(false);
  });

  it("ignores hover while held and closes a beat after release when the pointer is away", () => {
    mount();
    call(() => popup.show());
    call(() => popup.hold(true));
    call(() => popup.panel.onPointerEnter());
    call(() => popup.panel.onPointerLeave());
    wait(1000);
    expect(popup.open).toBe(true);
    call(() => popup.hold(false));
    wait(CLOSE_DELAY_MS - 1);
    expect(popup.open).toBe(true);
    wait(1);
    expect(popup.open).toBe(false);
  });

  it("stays open on release with the pointer over it", () => {
    mount();
    call(() => popup.show());
    call(() => popup.hold(true));
    call(() => popup.trigger.onPointerEnter());
    call(() => popup.hold(false));
    wait(1000);
    expect(popup.open).toBe(true);
  });

  it("doesn't open from a rest on the trigger while held", () => {
    mount();
    call(() => popup.hold(true));
    call(() => popup.trigger.onPointerEnter());
    wait(1000);
    expect(popup.open).toBe(false);
  });
});
