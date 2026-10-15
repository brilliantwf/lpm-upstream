import { describe, expect, it } from "vitest";
import { miniSize } from "./fileViewerLayout";

describe("miniSize", () => {
  it("hugs a portrait recording at its aspect, under the header", () => {
    expect(miniSize({ w: 1206, h: 2622 }, true)).toEqual({ w: 260, h: 396 });
  });

  it("fits a landscape video to the box width", () => {
    expect(miniSize({ w: 1920, h: 1080 }, true)).toEqual({ w: 384, h: 252 });
  });

  it("never scales a small image up", () => {
    expect(miniSize({ w: 300, h: 200 }, true)).toEqual({ w: 300, h: 236 });
  });

  it("waits for a video's size in a 16:9 box", () => {
    expect(miniSize(null, true)).toEqual({ w: 384, h: 252 });
  });

  it("gives text a fixed card", () => {
    expect(miniSize(null, false)).toEqual({ w: 420, h: 320 });
  });
});
