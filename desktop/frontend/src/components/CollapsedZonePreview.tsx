import type { ZoneDisplay, ZoneInfo } from "../types";
import { collapsedZoneClasses } from "./CollapsedZone";
import { ChevronDownIcon, ExpandIcon } from "./icons";
import { SPLIT_TONE } from "./SplitButton";

export function CollapsedZonePreview({ zone, display, editable }: { zone: ZoneInfo; display: ZoneDisplay; editable: boolean }) {
  const classes = collapsedZoneClasses(display, editable);
  const text = SPLIT_TONE[display].text;
  return (
    <div className={classes.root}>
      <span className={`${classes.label} ${text}`}>
        {zone.label}
        <ChevronDownIcon />
      </span>
      {editable && (
        <span className={`${classes.expand} ${text}`}>
          <ExpandIcon />
        </span>
      )}
    </div>
  );
}
