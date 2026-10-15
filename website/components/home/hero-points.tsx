import { Check } from "lucide-react";

const POINTS = [
  "Start every service in one click",
  "Give each agent its own copy",
  "See which agent needs you",
];

export function HeroPoints() {
  return (
    <ul className="mx-auto mt-4 flex w-fit flex-col items-start gap-y-1.5 text-[15px] text-gray-700 sm:w-auto sm:flex-row sm:flex-wrap sm:justify-center sm:gap-x-6 dark:text-gray-300">
      {POINTS.map((point) => (
        <li key={point} className="inline-flex items-center gap-2">
          <Check
            className="h-4 w-4 shrink-0 text-gray-400 dark:text-gray-500"
            strokeWidth={2.5}
            aria-hidden
          />
          {point}
        </li>
      ))}
    </ul>
  );
}
