import { useEffect, useState, type ReactNode } from "react";
import { CancelUpdate, InstallUpdate, RollbackUpdate } from "../../bridge/commands";
import { EventsOn } from "../../bridge/runtime";
import { useAppStore } from "../store/app";
import { useGlobalAgentStatus } from "../store/globalAgentStatus";
import {
  useUpdateInstallState,
  type UpdateInstallState,
  type UpdatePhase,
} from "../store/updateInstall";
import { busyAgentPlaces, type BusyAgentPlace } from "../updateBusyAgents";
import { UpdateBusyAgentsDialog } from "./UpdateBusyAgentsDialog";

function isUpdatePhase(value: string): value is UpdatePhase {
  return value === "checking" || value === "downloading" || value === "installing";
}

const setInstall = (patch: Partial<UpdateInstallState>) => useUpdateInstallState.setState(patch);

/** An update or a rollback: either resolves only when cancelled, since a
 *  finished install restarts the app. */
type Run = () => Promise<unknown>;

async function install(run: Run) {
  setInstall({ active: true, phase: "checking", progress: -1, cancelling: false, error: "" });
  try {
    await run();
  } catch (err) {
    setInstall({ error: String(err) });
  } finally {
    setInstall({ active: false, cancelling: false });
  }
}

async function cancel() {
  setInstall({ cancelling: true });
  const stopping = await CancelUpdate().catch(() => false);
  if (!stopping) setInstall({ cancelling: false });
}

export interface UpdateInstall extends UpdateInstallState {
  request: () => void;
  /** Install the newest stable release over a newer Beta build. */
  rollback: () => void;
  cancel: () => void;
  dismissError: () => void;
  dialogs: ReactNode;
}

/** Installing an update in place: a warning first when agents are mid-task,
 *  then progress the caller shows, which can cancel until the app starts being
 *  replaced. `dialogs` renders the warning; mount it once beside the caller's
 *  other modals. */
export function useUpdateInstall(): UpdateInstall {
  const state = useUpdateInstallState();
  const [busy, setBusy] = useState<{ places: BusyAgentPlace[]; run: Run } | null>(null);

  useEffect(() => EventsOn("update-progress", (pct: number) => setInstall({ progress: pct })), []);
  // A paired Mac can start the install too, so its steps alone say one is
  // running, and "idle" says one this app didn't start has stopped.
  useEffect(
    () =>
      EventsOn("update-status", (status: string) => {
        if (isUpdatePhase(status)) setInstall({ active: true, phase: status });
        else if (status === "idle") setInstall({ active: false, cancelling: false });
      }),
    [],
  );

  const request = (run: Run) => {
    const places = busyAgentPlaces(
      useAppStore.getState().projects,
      useGlobalAgentStatus.getState().entries,
    );
    if (places.length > 0) setBusy({ places, run });
    else void install(run);
  };

  const dialogs: ReactNode = (
    <UpdateBusyAgentsDialog
      places={busy?.places ?? null}
      onCancel={() => setBusy(null)}
      onConfirm={() => {
        setBusy(null);
        if (busy) void install(busy.run);
      }}
    />
  );

  return {
    ...state,
    request: () => request(InstallUpdate),
    rollback: () => request(RollbackUpdate),
    cancel: () => void cancel(),
    dismissError: () => setInstall({ error: "" }),
    dialogs,
  };
}
