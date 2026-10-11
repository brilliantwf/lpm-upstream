// @vitest-environment happy-dom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ZoneGridRemeasure } from "./ZoneGridRemeasure";

const measure = vi.fn();
vi.mock("@dnd-kit/core", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@dnd-kit/core")>()),
  useDndContext: () => ({ measureDroppableContainers: measure }),
}));

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  measure.mockClear();
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

const IDS = ["actions-group:zone:tools", "zone-dots:tools"];
const render = (buttons: string[]) => act(() => root.render(<ZoneGridRemeasure buttons={buttons} ids={IDS} />));

describe("ZoneGridRemeasure", () => {
  it("measures the grid's drop area and dots again when its buttons change", () => {
    render(["ios", "web"]);
    measure.mockClear();
    render(["ios"]);
    expect(measure).toHaveBeenCalledExactlyOnceWith(IDS);
  });

  it("leaves them be while the buttons stay", () => {
    render(["ios", "web"]);
    measure.mockClear();
    render(["ios", "web"]);
    expect(measure).not.toHaveBeenCalled();
  });
});
