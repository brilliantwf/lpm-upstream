import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { EventsOn } from "../../bridge/runtime";
import {
  ClearJobState,
  ClearJobStateGlobal,
  ListAllJobs,
  MarkAllJobsSeen,
  MarkJobSeen,
  StopJobRun,
} from "../../bridge/commands";
import { useAppStore } from "../store/app";
import { useContentZoom } from "../hooks/useContentZoom";
import { useJobCommands } from "../hooks/useJobCommands";
import { ClockIcon, PlusIcon } from "./icons";
import { EmptyState } from "./ui/EmptyState";
import { AutomationsHeader, type AutomationsViewMode } from "./AutomationsHeader";
import { deleteJob, deleteJobGlobal } from "../jobsConfig";
import type { JobInfo } from "../jobsFormat";
import { isUnread, jobScopeLabel, sortJobsForList } from "../jobsList";
import type { ScheduledJob } from "../hooks/useJobs";
import { displayNameForProjectName } from "./ProjectNameDisplay";
import { JobListSections } from "./project-detail/jobs/JobListSections";
import { JobMessages } from "./project-detail/jobs/JobMessages";
import { JobTaskView } from "./project-detail/jobs/JobTaskView";
import { JobDialogs, type JobEditing } from "./project-detail/jobs/JobDialogs";
import { ScheduleWeekView } from "./project-detail/jobs/schedule/ScheduleWeekView";

// One reader zoom for the whole section — the list, a job's runs, and a run's
// conversation all read at the level the user last picked.
const ZOOM_KEY = "lpm.automations-zoom";
const VIEW_KEY = "lpm.automations-view";

function storedView(): AutomationsViewMode {
  return localStorage.getItem(VIEW_KEY) === "week" ? "week" : "list";
}

// The folders a row's job runs in: a standalone job's is the sentinel "";
// project/repo and single-target rows carry a one-entry `targets`; a shared
// job carries all of them.
function targetsOfRow(job: JobInfo): string[] {
  return job.standalone ? [""] : job.targets ?? [];
}

// A representative project for opening / editing / removing a row: standalone
// has none, otherwise the first target.
function rowProject(job: ScheduledJob): string {
  return job.standalone ? "" : job.targets?.[0] ?? job.project ?? "";
}

interface ScheduledViewProps {
  // A project's Automations tab: only the jobs that run in it, and a new job
  // starts scoped to it. Absent = the app-wide page, every job.
  project?: string;
  // A hidden tab stops refetching on every run, and only the focused pane's
  // tab takes ⌘+ / ⌘−.
  visible?: boolean;
  focused?: boolean;
}

export function ScheduledView({ project, visible = true, focused = true }: ScheduledViewProps) {
  const projects = useAppStore((s) => s.projects);
  const selectProject = useAppStore((s) => s.selectProject);
  const [rows, setRows] = useState<ScheduledJob[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<JobEditing>(null);
  const [open, setOpen] = useState<{ project: string; id: string } | null>(null);
  // The run page open inside the job page — a run entry's `at`.
  const [openTask, setOpenTask] = useState<number | null>(null);
  const [removing, setRemoving] = useState<{ project: string; job: JobInfo } | null>(null);
  // A multi-project job awaiting the "which projects?" pick before a manual run.
  const [runPick, setRunPick] = useState<ScheduledJob | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [view, setView] = useState<AutomationsViewMode>(storedView);
  const selectView = (next: AutomationsViewMode) => {
    setView(next);
    localStorage.setItem(VIEW_KEY, next);
  };
  // The job editor has its own text surfaces, so it takes ⌘+/⌘− back while open.
  const zoom = useContentZoom(visible && focused && editing === null, ZOOM_KEY);

  const refetch = useCallback(async () => {
    try {
      const result = (await ListAllJobs()) as ScheduledJob[];
      setRows(Array.isArray(result) ? result : []);
      setError(null);
      setRefreshKey((n) => n + 1);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : String(err),
      );
      setRows((prev) => prev ?? []);
    }
  }, []);

  const { runNow, stopRun, toggleEnabled } = useJobCommands(refetch);

  useEffect(() => {
    if (!visible) return;
    void refetch();
    const cancel = EventsOn("job-status", () => void refetch());
    return () => {
      if (typeof cancel === "function") cancel();
    };
  }, [visible, refetch]);

  // In a project's tab a shared job opens on this project's own runs.
  const rowProjectOf = useCallback(
    (job: ScheduledJob) => project ?? rowProject(job),
    [project],
  );

  // Row buttons act on every folder the job runs in, so a shared job's Stop /
  // pause reach all of its projects at once.
  // A manual run in a job with more than one project first asks which to run;
  // a single-project or standalone job runs straight away.
  const runNowJob = (job: ScheduledJob) => {
    const targets = targetsOfRow(job).filter((p) => p !== "");
    if (targets.length > 1) {
      setRunPick(job);
    } else {
      targetsOfRow(job).forEach((p) => runNow(p, job.id));
    }
  };
  const stopRunJob = (job: JobInfo) =>
    targetsOfRow(job).forEach((p) => stopRun(p, job.id));
  const toggleEnabledJob = (job: JobInfo, enabled: boolean) =>
    targetsOfRow(job).forEach((p) => toggleEnabled(p, job.id, enabled));

  // Opening a run reads that run and everything before it, in every folder the
  // job runs in — the row folds them into one unread count.
  const markSeenJob = async (job: ScheduledJob, at?: number) => {
    await Promise.all(
      targetsOfRow(job).map((p) => MarkJobSeen(p, job.id, at).catch(() => {})),
    );
    void refetch();
  };

  // One flat feed: everything unread first, newest activity first inside each
  // half — the project each job runs in rides on the row itself.
  const shown = project
    ? (rows ?? []).filter((row) => targetsOfRow(row).includes(project))
    : rows ?? [];
  const sorted = sortJobsForList(shown);
  const unreadRows = sorted.filter(isUnread);
  const readRows = sorted.filter((row) => !isUnread(row));
  const unreadCount = unreadRows.length;
  const projectLabel = useCallback(
    (name: string) => displayNameForProjectName(name, projects),
    [projects],
  );
  const scopeLabelFor = useCallback(
    (job: ScheduledJob) => jobScopeLabel(job, projects.length, projectLabel),
    [projects.length, projectLabel],
  );
  // Stable so the board's memoised layout survives a render that changed
  // nothing about the jobs themselves.
  const rowKeyOf = useCallback(
    (job: ScheduledJob) => `${rowProject(job)}/${job.id}`,
    [],
  );

  const markAllSeen = async () => {
    try {
      if (project) {
        await Promise.all(
          unreadRows.flatMap((job) => targetsOfRow(job).map((p) => MarkJobSeen(p, job.id))),
        );
      } else {
        await MarkAllJobsSeen();
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : String(err));
    } finally {
      void refetch();
    }
  };

  const actionsFor = (project: string) =>
    projects.find((p) => p.name === project)?.actions ?? [];

  // Every job id in use (null = anywhere) — so a new job never silently takes
  // over an id another config layer already uses.
  const knownIds = (project: string | null) =>
    (rows ?? [])
      .filter((r) => project === null || r.project === project)
      .map((r) => r.id);

  const openJobPage = (job: ScheduledJob) =>
    setOpen({ project: rowProjectOf(job), id: job.id });
  const editJob = (job: ScheduledJob) =>
    setEditing({ mode: "edit", project: rowProjectOf(job), job });
  const removeJobRow = (job: ScheduledJob) =>
    setRemoving({ project: rowProjectOf(job), job });
  const newJob = () => setEditing({ mode: "new", project });

  const removeJob = async (deleteCopies: boolean) => {
    if (!removing) return;
    const { project, job } = removing;
    setRemoving(null);
    try {
      void StopJobRun(project, job.id);
      if (job.source === "global") {
        await deleteJobGlobal(job.id);
        await ClearJobStateGlobal(job.id, deleteCopies);
      } else {
        await deleteJob(project, job.id);
        await ClearJobState(project, job.id, deleteCopies);
      }
      toast.success("Job removed");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : String(err));
    } finally {
      void refetch();
    }
  };

  // Whether the job on the removal block has a run alive anywhere — a global
  // job rows under every project, and a copy can't be deleted under a live
  // run (the backend refuses too; this keeps the dialog honest up front).
  const removingRunning = removing
    ? shown.some(
        (r) =>
          r.id === removing.job.id &&
          r.running === true &&
          rowProjectOf(r) === rowProjectOf(removing.job),
      )
    : false;

  const openJob = open
    ? shown.find((r) => r.id === open.id && rowProjectOf(r) === open.project)
    : undefined;
  useEffect(() => {
    if (open && rows !== null && !openJob) {
      setOpen(null);
      setOpenTask(null);
    }
  }, [open, rows, openJob]);

  // The list and a job's own page raise the same dialogs.
  const dialogs = (
    <JobDialogs
      projects={projects}
      editing={editing}
      onCloseEditor={() => setEditing(null)}
      onSaved={() => void refetch()}
      actionsFor={actionsFor}
      knownIds={knownIds}
      removing={removing}
      removingRunning={removingRunning}
      onCancelRemove={() => setRemoving(null)}
      onConfirmRemove={(deleteCopies) => void removeJob(deleteCopies)}
      runPick={runPick}
      onCancelRunPick={() => setRunPick(null)}
      onRunPicked={(selected) => {
        const job = runPick;
        setRunPick(null);
        if (job) selected.forEach((p) => runNow(p, job.id));
      }}
    />
  );

  if (open && openJob && openTask !== null) {
    return (
      <JobTaskView
        project={open.project}
        job={openJob}
        rootAt={openTask}
        refreshKey={refreshKey}
        zoom={zoom}
        onBack={() => setOpenTask(null)}
        onStop={() => stopRun(open.project, openJob.id)}
        onChanged={() => void refetch()}
        onOpenCopy={(name) => selectProject(name)}
      />
    );
  }

  if (open && openJob) {
    return (
      <>
        <JobMessages
          project={open.project}
          job={openJob}
          refreshKey={refreshKey}
          zoom={zoom}
          onBack={() => setOpen(null)}
          onEdit={() =>
            setEditing({ mode: "edit", project: open.project, job: openJob })
          }
          onRunNow={() => runNowJob(openJob)}
          onStop={() => stopRunJob(openJob)}
          onRemove={() => setRemoving({ project: open.project, job: openJob })}
          onChanged={() => void refetch()}
          onToggleEnabled={(enabled) => toggleEnabledJob(openJob, enabled)}
          onOpenCopy={(name) => selectProject(name)}
          onOpenTask={(at) => setOpenTask(at)}
          onSeenUpTo={(at) => void markSeenJob(openJob, at)}
        />
        {dialogs}
      </>
    );
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
      <AutomationsHeader
        scoped={project !== undefined}
        hasJobs={shown.length > 0}
        unreadCount={unreadCount}
        view={view}
        onViewChange={selectView}
        zoom={zoom}
        onMarkAllSeen={() => void markAllSeen()}
        onNewJob={newJob}
      />

      <div
        ref={zoom.surfaceRef}
        className="min-h-0 flex-1 overflow-x-hidden overflow-y-auto pb-6 pt-4"
      >
        {error && !shown.length ? (
          <EmptyState title="Couldn't load scheduled jobs" body={error}>
            <button
              type="button"
              onClick={() => void refetch()}
              className="mt-4 rounded-lg bg-[var(--text-primary)] px-3.5 py-2 text-[13px] font-medium text-[var(--bg-primary)] shadow-sm transition hover:opacity-90"
            >
              Try again
            </button>
          </EmptyState>
        ) : rows === null ? (
          <p className="py-8 text-center text-[12px] text-[var(--text-muted)]">
            Loading…
          </p>
        ) : shown.length === 0 ? (
          <EmptyState
            icon={<ClockIcon />}
            title="Nothing scheduled yet"
            body={
              project
                ? "Create a job to run an AI prompt, a command, or an action in this project on a schedule."
                : "Create a job to run an AI prompt, a command, or an action on a schedule — for one project or all of them."
            }
          >
            <button
              type="button"
              onClick={newJob}
              className="mt-4 flex items-center gap-1.5 rounded-lg bg-[var(--text-primary)] px-3.5 py-2 text-[13px] font-medium text-[var(--bg-primary)] shadow-sm transition hover:opacity-90"
            >
              <PlusIcon />
              New job
            </button>
          </EmptyState>
        ) : (
          <>
            {error && (
              <p className="mb-3 rounded-lg bg-[color-mix(in_srgb,var(--accent-red)_12%,var(--bg-primary))] px-3 py-2 text-[11px] leading-snug text-[var(--accent-red-text)]">
                Couldn't refresh scheduled jobs: {error}
              </p>
            )}
            {view === "week" ? (
              <div className="min-w-0" style={{ zoom: zoom.zoom }}>
                <ScheduleWeekView
                  jobs={sorted}
                  scopeLabelFor={scopeLabelFor}
                  keyOf={rowKeyOf}
                  projectLabel={projectLabel}
                  onOpen={openJobPage}
                  onRunNow={runNowJob}
                  onStop={stopRunJob}
                />
              </div>
            ) : (
              <JobListSections
                unreadRows={unreadRows}
                readRows={readRows}
                zoom={zoom.zoom}
                scopeLabelFor={scopeLabelFor}
                rowKeyOf={rowKeyOf}
                onRunNow={runNowJob}
                onStop={stopRunJob}
                onToggleEnabled={toggleEnabledJob}
                onOpen={openJobPage}
                onEdit={editJob}
                onRemove={removeJobRow}
              />
            )}
          </>
        )}
      </div>

      {dialogs}
    </div>
  );
}
