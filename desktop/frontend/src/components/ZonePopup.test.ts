import { describe, expect, it } from "vitest";
import { placePopup } from "./ZonePopup";

const rect = (left: number, top: number, width: number, height: number) =>
  ({ left, top, right: left + width, bottom: top + height, width, height }) as DOMRect;
const viewport = { width: 1000, height: 800 };

describe("placePopup", () => {
  it("hangs below the button from its left edge in the header", () => {
    expect(placePopup(rect(100, 10, 80, 32), 300, "below", viewport)).toEqual({ left: 100, top: 48 });
  });

  it("rises above the button in the footer", () => {
    expect(placePopup(rect(100, 760, 80, 26), 300, "above", viewport)).toEqual({ left: 100, bottom: 46 });
  });

  it("aligns to the button's right edge when the left edge would run off the window", () => {
    expect(placePopup(rect(850, 10, 80, 32), 300, "below", viewport)).toEqual({ left: 630, top: 48 });
  });

  it("never starts left of the window margin", () => {
    expect(placePopup(rect(20, 10, 40, 32), 990, "below", viewport).left).toBe(8);
  });
});
