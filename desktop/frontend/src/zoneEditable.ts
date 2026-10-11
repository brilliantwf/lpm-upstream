import { isPeerName } from "./peer/markers";
import type { ZoneInfo } from "./types";

// A peer project's repo and global zones are out of reach: a global edit would
// land in this Mac's global.yml, and the host refuses a peer's repo save.
export function isZoneEditable(projectName: string, zone: Pick<ZoneInfo, "source">): boolean {
  return !isPeerName(projectName) || zone.source === "project";
}
