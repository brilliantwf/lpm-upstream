// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { captureDictationTarget, insertDictation, spaceDictation } from "./insert";

// happy-dom has no execCommand; a refused one sends text fields down the
// setRangeText path, which is what these tests then check.
beforeEach(() => {
  document.execCommand = () => false;
});

afterEach(() => {
  document.body.innerHTML = "";
});

describe("spaceDictation", () => {
  it("separates the text from words on either side", () => {
    expect(spaceDictation("Fix the", "login bug", "today")).toBe(" login bug ");
  });

  it("adds nothing beside whitespace or at the field's edges", () => {
    expect(spaceDictation("", "Hello", "")).toBe("Hello");
    expect(spaceDictation("line one\n", "Hello", " there")).toBe("Hello");
    expect(spaceDictation("ends with space ", "next", "")).toBe("next");
  });

  it("keeps opening brackets and quotes tight to the text", () => {
    expect(spaceDictation("call (", "the helper", ")")).toBe("the helper");
    expect(spaceDictation('say "', "hi", '"')).toBe("hi");
  });

  it("does not space before closing punctuation", () => {
    expect(spaceDictation("Done", ", thanks", "")).toBe(", thanks");
    expect(spaceDictation("", "Hello", ". More")).toBe("Hello");
  });

  it("trims what the transcriber returns and drops blank text", () => {
    expect(spaceDictation("a", "  word  ", "")).toBe(" word");
    expect(spaceDictation("a", "   ", "b")).toBe("");
  });
});

describe("captureDictationTarget", () => {
  it("captures a focused textarea with its caret", () => {
    const area = document.createElement("textarea");
    area.value = "hello world";
    document.body.append(area);
    area.focus();
    area.setSelectionRange(5, 5);
    expect(captureDictationTarget()).toEqual({ element: area, caret: { start: 5, end: 5 } });
  });

  it("ignores elements that don't take text", () => {
    const button = document.createElement("button");
    document.body.append(button);
    expect(captureDictationTarget(button)).toBeNull();
    const area = document.createElement("textarea");
    area.readOnly = true;
    expect(captureDictationTarget(area)).toBeNull();
    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    expect(captureDictationTarget(checkbox)).toBeNull();
    expect(captureDictationTarget(null)).toBeNull();
  });
});

describe("insertDictation", () => {
  it("inserts at the caret captured at the start, even after focus moved", () => {
    const area = document.createElement("textarea");
    area.value = "Please the tests";
    const other = document.createElement("input");
    document.body.append(area, other);
    area.focus();
    area.setSelectionRange(6, 6);
    const target = captureDictationTarget();
    other.focus();
    let inputs = 0;
    area.addEventListener("input", () => inputs++);

    expect(insertDictation(target, "run")).toBe(true);
    expect(area.value).toBe("Please run the tests");
    expect(document.activeElement).toBe(area);
    expect(inputs).toBe(1);
  });

  it("reports a field that is gone so the caller can fall back", () => {
    const area = document.createElement("textarea");
    document.body.append(area);
    area.focus();
    const target = captureDictationTarget();
    area.remove();
    expect(insertDictation(target, "lost words")).toBe(false);
    expect(insertDictation(null, "no field")).toBe(false);
  });
});
