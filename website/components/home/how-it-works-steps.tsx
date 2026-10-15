const STEPS = [
  {
    title: "Add a folder",
    body: "Pick a project folder or paste a Git URL. lpm finds the dev servers inside and sets them up for you.",
  },
  {
    title: "Start everything",
    body: "Press Start and every service boots at once, each with its live output in a tab of its own.",
  },
  {
    title: "Run agents in parallel",
    body: "Open Claude Code or Codex with one click. Duplicate the project to give each agent its own copy.",
  },
];

export function HowItWorksSteps() {
  return (
    <ol className="grid gap-3 sm:gap-4 md:grid-cols-3">
      {STEPS.map(({ title, body }, i) => (
        <li
          key={title}
          className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.02]"
        >
          <span className="flex items-center gap-3">
            <span
              aria-hidden
              className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-gray-900 text-[13px] font-semibold text-white tabular-nums dark:bg-white dark:text-gray-900"
            >
              {i + 1}
            </span>
            <h3 className="text-base font-semibold tracking-tight text-gray-900 dark:text-gray-100">
              <span className="sr-only">Step {i + 1}: </span>
              {title}
            </h3>
          </span>
          <p className="mt-2.5 text-sm leading-relaxed text-gray-600 dark:text-gray-400">
            {body}
          </p>
        </li>
      ))}
    </ol>
  );
}
