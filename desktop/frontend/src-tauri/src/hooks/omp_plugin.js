// lpm status plugin: shows this omp's turns as the status of the lpm terminal
// tab it runs in. lpm writes this file and rewrites it when it changes.
// # lpm-hook

// The main session's turn, from omp's events. Sub-agents work inside it and omp
// runs them without asking anything, so they report nothing of their own. A
// turn that stops only to go on — a retry, a background agent's result — says
// so with willContinue. A session left mid-turn (/new) never ends its turn, so
// switching ends it.
export default function lpmStatus(pi) {
  const turns = lpmTurns()
  const asks = new Set()
  const session = (ctx) => (ctx.agent?.kind === "sub" ? undefined : ctx.sessionManager?.getSessionId?.())

  const asked = (ctx, id) => {
    const s = session(ctx)
    if (!s) return
    asks.add(id)
    turns.enter(s, "waiting")
  }
  const answered = (ctx, id) => {
    const s = session(ctx)
    if (s && asks.delete(id) && !asks.size && turns.state(s) === "waiting") turns.enter(s, "running")
  }
  const endOthers = (s) => {
    asks.clear()
    for (const id of turns.live()) if (id !== s) turns.end(id, "clear")
  }

  pi.on("agent_start", (_event, ctx) => {
    const s = session(ctx)
    if (s && turns.state(s) !== "waiting") turns.enter(s, "running")
  })
  pi.on("tool_approval_requested", (event, ctx) => asked(ctx, `approval ${event.toolCallId}`))
  pi.on("tool_approval_resolved", (event, ctx) => answered(ctx, `approval ${event.toolCallId}`))
  pi.on("tool_execution_start", (event, ctx) => {
    if (event.toolName === "ask") asked(ctx, `ask ${event.toolCallId}`)
  })
  pi.on("tool_execution_end", (event, ctx) => {
    if (event.toolName === "ask") answered(ctx, `ask ${event.toolCallId}`)
  })
  pi.on("agent_end", (event, ctx) => {
    const s = session(ctx)
    if (!s || event.willContinue) return
    asks.clear()
    turns.end(s, ending(event.messages))
  })
  pi.on("session_switch", (_event, ctx) => {
    if (ctx.agent?.kind !== "sub") endOthers(session(ctx))
  })
  pi.on("session_shutdown", (_event, ctx) => {
    if (ctx.agent?.kind === "sub") return
    endOthers()
    return turns.settled()
  })
}

// Interrupted, a turn is one the user ended, not one that finished.
function ending(messages = []) {
  const stop = messages.findLast((message) => message.role === "assistant")?.stopReason
  return stop === "aborted" ? "clear" : stop === "error" ? "error" : "done"
}
