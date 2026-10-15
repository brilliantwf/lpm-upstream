import { create } from "zustand";
import { CheckForUpdate } from "../../bridge/commands";
import type { UpdateChannel } from "./settings";

export interface UpdateInfo {
  currentVersion: string;
  /** The newest release on `channel`. */
  latestVersion: string;
  updateAvail: boolean;
  channel: UpdateChannel;
  /** The newest stable release, which a rollback installs. */
  stableVersion: string;
  /** This build is newer than the newest stable release. */
  rollbackAvail: boolean;
}

// The last answer from the release check, whoever asked, so the sidebar's
// update row and Settings agree — and a channel switch updates both.
export const useUpdateCheck = create<{ info: UpdateInfo | null }>(() => ({ info: null }));

export function noteUpdateCheck(info: UpdateInfo) {
  useUpdateCheck.setState({ info });
}

export async function checkForUpdate(): Promise<UpdateInfo> {
  const info: UpdateInfo = await CheckForUpdate();
  noteUpdateCheck(info);
  return info;
}
