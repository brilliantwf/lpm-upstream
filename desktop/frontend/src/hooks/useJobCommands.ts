import { useCallback } from "react";
import { toast } from "sonner";
import { RunJobNow, SetJobEnabled, StopJobRun } from "../../bridge/commands";

const errorText = (err: unknown) => (err instanceof Error ? err.message : String(err));

// Automations row buttons, for one job in one project. They fire-and-forget; a
// rejection (invalid job, gone project) must still reach the user instead of
// dying in a void promise.
export function useJobCommands(refetch: () => Promise<void>) {
  const runNow = useCallback((project: string, id: string) => {
    RunJobNow(project, id).catch((err) => toast.error(errorText(err)));
  }, []);
  const stopRun = useCallback((project: string, id: string) => {
    StopJobRun(project, id).catch((err) => toast.error(errorText(err)));
  }, []);
  const toggleEnabled = useCallback(
    (project: string, id: string, enabled: boolean) => {
      SetJobEnabled(project, id, enabled)
        .catch((err) => toast.error(errorText(err)))
        .finally(() => void refetch());
    },
    [refetch],
  );
  return { runNow, stopRun, toggleEnabled };
}
