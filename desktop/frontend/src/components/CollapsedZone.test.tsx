// @vitest-environment happy-dom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { DndContext } from "@dnd-kit/core";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ZoneInfo } from "../types";
import { CLOSE_DELAY_MS } from "../hooks/useHoverPopup";
import { CollapsedZone } from "./CollapsedZone";
import { ZONE_SPRING_MS } from "./springLoad";

const zone: ZoneInfo = { name: "ship", label: "Ship", rows: 1, source: "project", collapsed: true };

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

interface Drag {
  dragging?: boolean;
  buttonDrag?: boolean;
  ownsDrag?: boolean;
}

function render({ dragging = false, buttonDrag = false, ownsDrag = false }: Drag = {}) {
  act(() =>
    root.render(
      <DndContext>
        <CollapsedZone
          zone={zone}
          display="header"
          editable
          dragging={dragging}
          buttonDrag={buttonDrag}
          ownsDrag={ownsDrag}
          onExpand={() => {}}
        >
          <button type="button">inside</button>
        </CollapsedZone>
      </DndContext>,
    ),
  );
}

const label = () => container.querySelector<HTMLButtonElement>("button[aria-haspopup]")!;
const popupEl = () => document.querySelector("[data-zone-popup]");
const wait = (ms: number) => act(() => vi.advanceTimersByTime(ms));
// React builds onPointerEnter/Leave from pointerover/pointerout.
const pointer = (el: Element, type: "pointerover" | "pointerout") =>
  act(() => {
    el.dispatchEvent(new MouseEvent(type, { bubbles: true, relatedTarget: null }));
  });

describe("CollapsedZone during a drag", () => {
  it("keeps the popup up for a drag that started in it", () => {
    render();
    act(() => label().click());
    render({ dragging: true, buttonDrag: true, ownsDrag: true });
    expect(popupEl()).not.toBeNull();
  });

  it("hides the popup when another drag starts", () => {
    render();
    act(() => label().click());
    render({ dragging: true, buttonDrag: true });
    expect(popupEl()).toBeNull();
  });

  it("springs the popup open after a rest during a button drag", () => {
    render({ dragging: true, buttonDrag: true });
    pointer(label(), "pointerover");
    wait(ZONE_SPRING_MS - 1);
    expect(popupEl()).toBeNull();
    wait(1);
    expect(popupEl()).not.toBeNull();
  });

  it("doesn't spring open for a zone drag", () => {
    render({ dragging: true });
    pointer(label(), "pointerover");
    wait(ZONE_SPRING_MS * 2);
    expect(popupEl()).toBeNull();
  });

  it("stops a spring when the pointer moves on", () => {
    render({ dragging: true, buttonDrag: true });
    pointer(label(), "pointerover");
    wait(ZONE_SPRING_MS - 100);
    pointer(label(), "pointerout");
    wait(ZONE_SPRING_MS);
    expect(popupEl()).toBeNull();
  });

  it("closes a beat after a drop away from it", () => {
    render({ dragging: true, buttonDrag: true });
    pointer(label(), "pointerover");
    wait(ZONE_SPRING_MS);
    pointer(label(), "pointerout");
    wait(1000);
    expect(popupEl()).not.toBeNull();
    render();
    wait(CLOSE_DELAY_MS);
    expect(popupEl()).toBeNull();
  });
});

describe("CollapsedZone and Escape", () => {
  const escape = () => act(() => void document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true })));

  afterEach(() => document.body.removeAttribute("data-lpm-drag"));

  it("closes the popup", () => {
    render();
    act(() => label().click());
    escape();
    expect(popupEl()).toBeNull();
  });

  it("leaves the popup up mid-drag, where Escape cancels the drag", () => {
    render();
    act(() => label().click());
    render({ dragging: true, buttonDrag: true, ownsDrag: true });
    document.body.setAttribute("data-lpm-drag", "");
    escape();
    expect(popupEl()).not.toBeNull();
  });
});
