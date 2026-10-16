package cx.lpm.mobile.core.protocol

import cx.lpm.mobile.core.model.HistoryCursor
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** The composer: autocomplete, AI actions and rewrites, draft sync and message history. */
sealed interface WireComposer {
    fun slash(id: String, project: String): String = frame("slash") {
        put("id", id)
        put("project", project)
    }

    fun mentions(project: String): String = frame("mentions") { put("project", project) }

    fun history(project: String, q: String): String = frame("history") {
        put("project", project)
        put("q", q)
    }

    fun historyAdd(project: String, id: String, label: String, text: String): String =
        frame("historyAdd") {
            put("project", project)
            put("id", id)
            put("label", label)
            put("text", text)
        }

    fun composerActions(): String = frame("composerActions")

    fun composerDraft(id: String, text: String): String = frame("composerDraft") {
        put("id", id)
        put("text", text)
    }

    fun transform(
        reqId: String,
        project: String,
        instruction: String,
        text: String,
        variants: Int
    ): String = frame("transform") {
        put("reqId", reqId)
        put("project", project)
        put("instruction", instruction)
        put("text", text)
        put("variants", variants)
    }

    fun historyQuery(
        project: String?,
        search: String?,
        favoritesOnly: Boolean,
        folder: String?,
        before: HistoryCursor?
    ): String = frame("historyQuery") {
        putNonEmpty("project", project)
        putNonEmpty("search", search)
        if (favoritesOnly) put("favoritesOnly", true)
        putNonEmpty("folder", folder)
        if (before != null) {
            putJsonObject("before") {
                put("at", before.at)
                put("seq", before.seq)
            }
        }
    }

    fun historySaveDraft(
        message: String,
        project: String?,
        id: String?,
        label: String?,
        images: Map<String, String>?
    ): String = frame("historySaveDraft") {
        put("message", message)
        putNonEmpty("project", project)
        putNonEmpty("id", id)
        putNonEmpty("label", label)
        if (!images.isNullOrEmpty()) putStringMap("images", images)
    }

    fun historyToggleFavorite(id: String): String = frame("historyToggleFavorite") { put("id", id) }

    fun historySetFolder(id: String, folder: String?): String = frame("historySetFolder") {
        put("id", id)
        putNonEmpty("folder", folder)
    }

    fun historyDelete(id: String): String = frame("historyDelete") { put("id", id) }

    fun historyFolders(): String = frame("historyFolders")

    fun historyCreateFolder(name: String): String = frame("historyCreateFolder") {
        put("name", name)
    }

    fun historyDeleteFolder(id: String?, name: String?): String = frame("historyDeleteFolder") {
        putNonEmpty("id", id)
        putNonEmpty("name", name)
    }
}
