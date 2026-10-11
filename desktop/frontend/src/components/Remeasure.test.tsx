// @vitest-environment happy-dom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Remeasure } from "./Remeasure";

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
const render = (buttons: string[]) => act(() => root.render(<Remeasure on={buttons.join("\n")} ids={IDS} />));

describe("Remeasure", () => {
  it("measures the drop targets again when what moves them changes", () => {
    render(["ios", "web"]);
    measure.mockClear();
    render(["ios"]);
    expect(measure).toHaveBeenCalledExactlyOnceWith(IDS);
  });

  it("leaves them be while it stays", () => {
    render(["ios", "web"]);
    measure.mockClear();
    render(["ios", "web"]);
    expect(measure).not.toHaveBeenCalled();
  });
});
