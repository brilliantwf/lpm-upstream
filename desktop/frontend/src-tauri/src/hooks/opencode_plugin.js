// lpm status plugin: shows this OpenCode's sessions as the status of the lpm
// terminal tab it runs in. lpm writes this file and rewrites it when it
// changes. # lpm-hook

// A top-level session's turn, from OpenCode's events. A sub-agent's session
// works inside its parent's turn, so its questions and approvals are the
// parent's and its own start and finish are not reported. A turn's error comes
// just before its idle, which is where the turn is reported as ended.
export const LpmStatus = async () => {
  const turns = lpmTurns()
  const parents = new Map()
  const asks = new Map()
  const endings = new Map()

  const top = (id) => {
    while (parents.has(id)) id = parents.get(id)
    return id
  }
  const end = (id, state) => {
    asks.delete(id)
    endings.delete(id)
    turns.end(id, state)
  }
  const asked = (sessionID, requestID) => {
    const id = top(sessionID)
    asks.set(id, (asks.get(id) ?? new Set()).add(requestID))
    turns.enter(id, "waiting")
  }
  const answered = (sessionID, requestID, rejected) => {
    const id = top(sessionID)
    asks.get(id)?.delete(requestID)
    // Turned down, the session stops: a turn the user ended, not one that finished.
    if (rejected && id === sessionID) endings.set(id, "clear")
    else if (!asks.get(id)?.size && turns.state(id) === "waiting") turns.enter(id, "running")
  }

  return {
    event: async ({ event }) => {
      const p = event.properties ?? {}
      switch (event.type) {
        case "session.created":
        case "session.updated":
          if (p.info?.parentID) parents.set(p.info.id, p.info.parentID)
          break
        case "session.status":
          if (parents.has(p.sessionID)) break
          if (p.status?.type === "idle") end(p.sessionID, endings.get(p.sessionID) ?? "done")
          else if (turns.state(p.sessionID) !== "waiting") {
            endings.delete(p.sessionID)
            turns.enter(p.sessionID, "running")
          }
          break
        case "session.error":
          if (p.sessionID && !parents.has(p.sessionID)) {
            endings.set(p.sessionID, p.error?.name === "MessageAbortedError" ? "clear" : "error")
          }
          break
        case "session.deleted":
          if (parents.has(p.info?.id)) parents.delete(p.info.id)
          else end(p.info?.id, "clear")
          break
        case "permission.asked":
        case "permission.updated":
        case "question.asked":
          asked(p.sessionID, p.id)
          break
        case "permission.replied":
          answered(p.sessionID, p.requestID ?? p.permissionID, (p.reply ?? p.response) === "reject")
          break
        case "question.replied":
        case "question.rejected":
          answered(p.sessionID, p.requestID, event.type === "question.rejected")
          break
      }
    },
    dispose: () => turns.settled(),
  }
}
