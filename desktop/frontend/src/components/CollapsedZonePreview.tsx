import type { ZoneDisplay, ZoneInfo } from "../types";
import { collapsedZoneClasses } from "./CollapsedZone";
import { ChevronDownIcon, ExpandIcon } from "./icons";

export function CollapsedZonePreview({ zone, display, editable }: { zone: ZoneInfo; display: ZoneDisplay; editable: boolean }) {
  const classes = collapsedZoneClasses(display, editable);
  return (
    <div className={classes.root}>
      <span className={`${classes.label} ${classes.text}`}>
        {zone.label}
        <ChevronDownIcon />
      </span>
      {editable && (
        <span className={`${classes.expand} ${classes.text}`}>
          <ExpandIcon />
        </span>
      )}
    </div>
  );
}
