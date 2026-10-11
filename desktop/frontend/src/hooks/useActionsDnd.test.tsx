// @vitest-environment happy-dom
import { act } from "react";
import { createRoot } from "react-dom/client";
import { describe, expect, it, vi } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";
import type { DragEndEvent, DragOverEvent, DragStartEvent } from "@dnd-kit/core";
import type { ActionsLayout } from "../types";
import { zoneDotsId, zoneFoldId, zoneItemId } from "../components/actionsDndLayout";
import { type UseActionsDndResult, useActionsDnd } from "./useActionsDnd";

const layout = (): ActionsLayout => ({
  header: ["build", "lint", zoneItemId("tools")],
  footer: ["logs"],
  zones: { tools: ["ios"], empty: [] },
});

// Renders the hook once and hands back its handlers; the state setters they
// call are no-ops after a server render, so only the callbacks are observable.
function mount(current: ActionsLayout, openListOf?: (zone: string) => string) {
  const onPreview = vi.fn();
  const onMove = vi.fn();
  const onDragActiveChange = vi.fn();
  let api!: UseActionsDndResult;
  function Probe() {
    api = useActionsDnd({
      layout: current,
      onPreview,
      onMove,
      onStructural: vi.fn(),
      canNest: () => false,
      isMenu: () => false,
      indicatorRef: { current: null },
      menuDropRef: { current: null },
      onDragActiveChange,
      openListOf,
    });
    return null;
  }
  renderToStaticMarkup(<Probe />);
  return { api, onPreview, onMove, onDragActiveChange };
}

const start = (id: string) => ({ active: { id } }) as unknown as DragStartEvent;
const over = (id: string, overId: string, x = 0, y = 0) =>
  ({ active: { id }, over: { id: overId }, delta: { x, y } }) as unknown as DragOverEvent;
const end = (id: string, overId: string) =>
  ({ active: { id }, over: { id: overId } }) as unknown as DragEndEvent;

describe("useActionsDnd previews", () => {
  it("previews a header button that moves over a zone's button", () => {
    const { api, onPreview } = mount(layout());
    api.onDragStart(start("lint"));
    api.onDragOver(over("lint", "ios", 10));
    expect(onPreview).toHaveBeenCalledTimes(1);
    const next: ActionsLayout = onPreview.mock.calls[0][0];
    expect(next.zones.tools).toEqual(["lint", "ios"]);
    expect(next.header).toEqual(["build", zoneItemId("tools")]);
  });

  it("does not preview a move inside the button's own row", () => {
    const { api, onPreview } = mount(layout());
    api.onDragStart(start("lint"));
    api.onDragOver(over("lint", "build", 10));
    expect(onPreview).not.toHaveBeenCalled();
  });

  it("previews every change of target, so the rows show where a drop lands", () => {
    // Collision detection holds its answer while the pointer stands still,
    // so a new target here always comes from a pointer move.
    const { api, onPreview } = mount(layout());
    api.onDragStart(start("lint"));
    api.onDragOver(over("lint", "ios", 10, 4));
    api.onDragOver(over("lint", "actions-group:zone:empty", 10, 4));
    expect(onPreview).toHaveBeenCalledTimes(2);
    expect(onPreview.mock.calls[1][0].zones.empty).toEqual(["lint"]);
  });

  it("commits against the layout the drag started from", () => {
    const start0 = layout();
    const { api, onMove } = mount(start0);
    api.onDragStart(start("lint"));
    api.onDragEnd(end("lint", "ios"));
    expect(onMove).toHaveBeenCalledTimes(1);
    const [next, before] = onMove.mock.calls[0];
    expect(before).toBe(start0);
    expect(next.zones.tools).toEqual(["lint", "ios"]);
  });
});

describe("useActionsDnd drag activity", () => {
  it("reports the drag from its start, end and cancel handlers, not from a later effect", () => {
    const { api, onDragActiveChange } = mount(layout());
    api.onDragStart(start("lint"));
    expect(onDragActiveChange.mock.calls).toEqual([[true]]);
    api.onDragEnd(end("lint", "build"));
    expect(onDragActiveChange.mock.calls).toEqual([[true], [false]]);
    api.onDragStart(start("lint"));
    api.onDragCancel();
    expect(onDragActiveChange.mock.calls).toEqual([[true], [false], [true], [false]]);
  });
});

describe("useActionsDnd after a cancel", () => {
  it("swallows the click a release over a button would send, until the next press", () => {
    const { api } = mount(layout());
    const button = document.createElement("button");
    const onClick = vi.fn();
    button.addEventListener("click", onClick);
    document.body.append(button);
    try {
      api.onDragStart(start("lint"));
      api.onDragCancel();
      button.click();
      expect(onClick).not.toHaveBeenCalled();
      button.click();
      expect(onClick).toHaveBeenCalledTimes(1);

      api.onDragStart(start("lint"));
      api.onDragCancel();
      window.dispatchEvent(new Event("pointerdown"));
      button.click();
      expect(onClick).toHaveBeenCalledTimes(2);
    } finally {
      button.remove();
    }
  });
});

describe("useActionsDnd over a zone's dots", () => {
  const layered = (): ActionsLayout => ({
    header: ["build", "lint", zoneItemId("tools")],
    footer: ["logs"],
    zones: { "tools/a": ["ios"], "tools/b": ["sim"] },
  });
  const openB = (zone: string) => (zone === "tools" ? "tools/b" : zone);

  it("moves nothing while the pointer is on them", () => {
    const { api, onPreview } = mount(layered(), openB);
    api.onDragStart(start("lint"));
    api.onDragOver(over("lint", zoneDotsId("tools")));
    expect(onPreview).not.toHaveBeenCalled();
  });

  it("drops the button at the end of the layer showing", () => {
    const { api, onMove } = mount(layered(), openB);
    api.onDragStart(start("lint"));
    api.onDragEnd(end("lint", zoneDotsId("tools")));
    expect(onMove).toHaveBeenCalledTimes(1);
    const next: ActionsLayout = onMove.mock.calls[0][0];
    expect(next.zones["tools/b"]).toEqual(["sim", "lint"]);
    expect(next.header).toEqual(["build", zoneItemId("tools")]);
  });

  it("treats a folded zone's button like its dots", () => {
    const { api, onMove, onPreview } = mount(layered(), openB);
    api.onDragStart(start("lint"));
    api.onDragOver(over("lint", zoneFoldId("tools")));
    expect(onPreview).not.toHaveBeenCalled();
    api.onDragEnd(end("lint", zoneFoldId("tools")));
    const next: ActionsLayout = onMove.mock.calls[0][0];
    expect(next.zones["tools/b"]).toEqual(["sim", "lint"]);
  });
});

describe("useActionsDnd settling on a folded zone", () => {
  function live() {
    (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
    let api!: UseActionsDndResult;
    function Probe() {
      api = useActionsDnd({
        layout: layout(),
        onPreview: vi.fn(),
        onMove: vi.fn(),
        onStructural: vi.fn(),
        canNest: () => false,
        isMenu: () => false,
        indicatorRef: { current: null },
        menuDropRef: { current: null },
      });
      return null;
    }
    const root = createRoot(document.createElement("div"));
    act(() => root.render(<Probe />));
    const drop = (overId: string) => {
      act(() => api.onDragStart(start("lint")));
      act(() => api.onDragEnd(end("lint", overId)));
      return api.settle;
    };
    return { drop, unmount: () => act(() => root.unmount()) };
  }

  it("shrinks away on the folded button, where the button has no slot to fly to", () => {
    const { drop, unmount } = live();
    expect(drop(zoneFoldId("tools"))).toBe("absorb");
    unmount();
  });

  it("still flies to its slot on a zone's dots", () => {
    const { drop, unmount } = live();
    expect(drop(zoneDotsId("tools"))).toBe("move");
    unmount();
  });
});
