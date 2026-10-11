// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ClientRect, CollisionDetection } from "@dnd-kit/core";
import type { ActionsLayout } from "../types";
import { createActionsCollision } from "./actionsCollision";
import { popupAt } from "./actionsDragMotion";
import { groupDropId, nestId, zoneFoldId, zoneGroup, zoneItemId } from "./actionsDndLayout";

type Args = Parameters<CollisionDetection>[0];

const box = (left: number, top: number, width: number, height: number): ClientRect => ({
  left,
  top,
  width,
  height,
  right: left + width,
  bottom: top + height,
});

const layout: ActionsLayout = {
  header: ["build", zoneItemId("tools"), zoneItemId("ship")],
  footer: [],
  zones: { tools: ["ios"], ship: ["prod"] },
};

// The tools popup hangs below its folded button, over the header's wrapped
// second line and the ship zone's folded button.
const POPUP = box(100, 40, 200, 60);

function scene() {
  const panel = document.createElement("div");
  panel.setAttribute("data-zone-popup", "");
  panel.getBoundingClientRect = () => ({ ...POPUP, x: POPUP.left, y: POPUP.top, toJSON: () => ({}) }) as DOMRect;
  document.body.appendChild(panel);
  const nodeIn = (parent: HTMLElement) => {
    const node = document.createElement("div");
    parent.appendChild(node);
    return node;
  };
  const rects: [string, ClientRect, HTMLElement][] = [
    [nestId("ios"), box(110, 50, 60, 30), nodeIn(panel)],
    ["ios", box(110, 50, 60, 30), nodeIn(panel)],
    [groupDropId(zoneGroup("tools")), box(105, 45, 190, 50), nodeIn(panel)],
    [zoneFoldId("ship"), box(100, 45, 80, 30), nodeIn(document.body)],
    [groupDropId("header"), box(0, 0, 400, 80), nodeIn(document.body)],
  ];
  return rects;
}

function argsAt(x: number, y: number, rects: [string, ClientRect, HTMLElement][]): Args {
  const collisionRect = box(x - 20, y - 12, 40, 24);
  return {
    active: { id: "build", data: { current: {} }, rect: { current: { initial: collisionRect, translated: collisionRect } } },
    collisionRect,
    droppableRects: new Map(rects.map(([id, rect]) => [id, rect])),
    droppableContainers: rects.map(([id, rect, node]) => ({
      id,
      key: id,
      disabled: false,
      node: { current: node },
      data: { current: {} },
      rect: { current: rect },
    })),
    pointerCoordinates: { x, y },
  } as unknown as Args;
}

function detectWith(overMenu: () => boolean = () => false) {
  const detect = createActionsCollision({
    layout,
    canNest: () => false,
    updateIndicator: vi.fn(),
    updateMenuDrop: vi.fn(),
    held: { current: null },
    overMenu,
    popupAt,
  });
  return (args: Args) => detect(args).map((c) => String(c.id));
}

afterEach(() => {
  document.body.innerHTML = "";
});

describe("a button dragged over a folded zone's popup", () => {
  it("targets the popup's own button, not the fold or row it covers", () => {
    expect(detectWith()(argsAt(130, 60, scene()))).toEqual(["ios"]);
  });

  it("leaves what lies outside the popup as it was", () => {
    const rects = scene().map(([id, rect, node]): [string, ClientRect, HTMLElement] =>
      id === zoneFoldId("ship") ? [id, box(320, 45, 60, 30), node] : [id, rect, node],
    );
    expect(detectWith()(argsAt(340, 60, rects))).toEqual([zoneFoldId("ship")]);
  });

  it("yields to a menu opened over the popup", () => {
    expect(detectWith(() => true)(argsAt(130, 60, scene()))).toEqual([zoneFoldId("ship")]);
  });
});
