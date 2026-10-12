// lpm's half of the plugin, the same for every agent: the agent's half above
// follows its turns, and this reports them to the app.
import { spawn } from "node:child_process"

// What each state runs: [program, args], with {"session_id": …} on its stdin.
const HOOKS = __LPM_HOOKS__

// The state each session's turn is in, reported as it changes. A turn ends
// once: ending one that isn't live reports nothing.
function lpmTurns() {
  const live = new Map()
  let reported = Promise.resolve()
  const report = (id, state) => {
    reported = reported.then(() => run(HOOKS[state], id))
  }
  return {
    state: (id) => live.get(id),
    live: () => [...live.keys()],
    enter(id, state) {
      if (live.get(id) === state) return
      live.set(id, state)
      report(id, state)
    },
    end(id, state) {
      if (live.delete(id)) report(id, state)
    },
    settled: () => reported,
  }
}

// One state at a time, in order: each hook delivers its report before the next
// starts. A hook that hangs is given up on rather than holding the rest back.
function run([program, args], sessionID) {
  return new Promise((done) => {
    setTimeout(done, 3000).unref?.()
    try {
      const child = spawn(program, args, { stdio: ["pipe", "ignore", "ignore"], windowsHide: true })
      child.on("error", done).on("exit", done)
      child.stdin.on("error", () => {})
      child.stdin.end(JSON.stringify({ session_id: sessionID }))
    } catch {
      done()
    }
  })
}
