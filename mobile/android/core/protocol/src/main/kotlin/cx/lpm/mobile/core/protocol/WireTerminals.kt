package cx.lpm.mobile.core.protocol

import kotlinx.serialization.json.put

/** Terminals, actions, background runs, uploads and file previews. */
sealed interface WireTerminals {
    fun terminals(project: String): String = frame("terminals") { put("project", project) }

    /**
     * Subscribe to a terminal's output. `from` is the byte-stream offset this phone
     * already applied: the Mac then replies with just the slice missed since
     * (keeping the screen as-is) instead of a full screen replay.
     */
    fun sub(id: String, from: Long? = null): String = frame("sub") {
        put("id", id)
        if (from != null) put("from", from)
    }

    fun unsub(id: String): String = frame("unsub") { put("id", id) }

    /** Take control of a terminal shown live elsewhere (the "Take control" button). */
    fun claim(id: String): String = frame("claim") { put("id", id) }

    fun input(id: String, data: String): String = frame("in") {
        put("id", id)
        put("d", data)
    }

    fun resize(id: String, cols: Int, rows: Int): String = frame("resize") {
        put("id", id)
        put("cols", cols)
        put("rows", rows)
    }

    fun newTerminal(project: String): String = frame("newTerminal") { put("project", project) }

    fun closeTerminal(project: String, id: String): String = frame("closeTerminal") {
        put("project", project)
        put("id", id)
    }

    fun renameTerminal(project: String, id: String, label: String): String =
        frame("renameTerminal") {
            put("project", project)
            put("id", id)
            put("label", label)
        }

    fun pinTerminal(project: String, id: String): String = frame("pinTerminal") {
        put("project", project)
        put("id", id)
    }

    fun reorderTerminals(project: String, order: List<String>): String = frame("reorderTerminals") {
        put("project", project)
        putStrings("order", order)
    }

    fun runAction(
        project: String,
        action: String,
        inputValues: Map<String, String> = emptyMap(),
        confirmed: Boolean = false
    ): String = frame("runAction") {
        put("project", project)
        put("action", action)
        putStringMap("inputValues", inputValues)
        put("confirmed", confirmed)
    }

    fun runActionBackground(
        project: String,
        action: String,
        inputValues: Map<String, String>,
        runId: String
    ): String = frame("runActionBackground") {
        put("project", project)
        put("action", action)
        putStringMap("inputValues", inputValues)
        put("runId", runId)
    }

    fun actionBgOutput(project: String, runId: String): String = frame("actionBgOutput") {
        put("project", project)
        put("runId", runId)
    }

    fun cancelActionBackground(runId: String): String = frame("cancelActionBackground") {
        put("runId", runId)
    }

    fun backgroundRuns(project: String): String = frame("backgroundRuns") {
        put("project", project)
    }

    /**
     * An upload carrying a per-upload `reqId` the server echoes back, so the phone
     * matches each reply to its chip independent of order. `name` saves the blob
     * under its original basename (files).
     */
    fun upload(id: String, data: String, mime: String, name: String?, reqId: String): String =
        frame("upload") {
            put("id", id)
            put("data", data)
            put("mime", mime)
            put("reqId", reqId)
            putNonEmpty("name", name)
        }

    /**
     * The chunked attach, for a Mac whose `features` has "fileUpload": begin with the
     * name and size, send each piece at its offset after the last one is
     * acknowledged, then `uploadDone` answers with the path to paste.
     */
    fun uploadBegin(id: String, name: String, bytes: Long, reqId: String): String =
        frame("uploadBegin") {
            put("id", id)
            put("name", name)
            put("bytes", bytes)
            put("reqId", reqId)
        }

    fun uploadChunk(
        id: String,
        uploadId: String,
        offset: Long,
        data: String,
        reqId: String
    ): String = frame("uploadChunk") {
        put("id", id)
        put("uploadId", uploadId)
        put("offset", offset)
        put("b64", data)
        put("reqId", reqId)
    }

    fun uploadDone(id: String, uploadId: String, reqId: String): String = frame("uploadDone") {
        put("id", id)
        put("uploadId", uploadId)
        put("reqId", reqId)
    }

    /** No `reqId`: the Mac answers an abort with nothing. */
    fun uploadAbort(id: String, uploadId: String): String = frame("uploadAbort") {
        put("id", id)
        put("uploadId", uploadId)
    }

    /**
     * One byte range of any file for the preview sheet. A relative `path` resolves
     * against `project`'s root; `reqId` routes the reply to its fetch.
     */
    fun fileChunk(
        reqId: String,
        project: String,
        path: String,
        offset: Long,
        length: Long
    ): String = frame("fileChunk") {
        put("reqId", reqId)
        put("project", project)
        put("path", path)
        put("offset", offset)
        put("length", length)
    }
}
