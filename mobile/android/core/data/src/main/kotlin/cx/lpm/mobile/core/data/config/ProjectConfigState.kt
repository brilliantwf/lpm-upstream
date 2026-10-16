package cx.lpm.mobile.core.data.config

import cx.lpm.mobile.core.model.DirListing
import cx.lpm.mobile.core.model.SshHostInfo
import kotlinx.serialization.json.JsonObject

/**
 * The add-project and config editors' seeds and their one shared write cluster: a
 * write sets [mutationInFlight] (the Save spinner) and, on success, bumps
 * [mutationDoneToken] so the open sheet dismisses; a failure sets [mutationError].
 */
data class ProjectConfigState(
    /** One level of the Mac's folders, for the folder browser. */
    val dirListing: DirListing? = null,
    val dirListingLoading: Boolean = false,
    val dirListingError: String? = null,
    /** The Mac's `~/.ssh/config` hosts; [sshHostsLoaded] tells empty from not yet loaded. */
    val sshHosts: List<SshHostInfo> = emptyList(),
    val sshHostsLoaded: Boolean = false,
    val sshHostsError: String? = null,
    /**
     * The raw YAML editor's seed: the layer's text, and whether the layer applies
     * here at all (not the repo layer of an SSH or rootless project).
     */
    val configText: String? = null,
    val configAvailable: Boolean = true,
    val configTextLoading: Boolean = false,
    val configTextError: String? = null,
    /** The service form's seed: the normalized mapping, and the layer that owns it. */
    val serviceBody: JsonObject? = null,
    val serviceBodySource: String = "",
    val serviceBodyLoading: Boolean = false,
    val serviceBodyError: String? = null,
    /**
     * The action form's seed: the normalized mapping, the section that holds it
     * (`actions` or `terminals`) and the layer that owns it.
     */
    val actionBody: JsonObject? = null,
    val actionBodySection: String = "actions",
    val actionBodySource: String = "",
    val actionBodyLoading: Boolean = false,
    val actionBodyError: String? = null,
    val mutationInFlight: Boolean = false,
    val mutationError: String? = null,
    val mutationDoneToken: Int = 0,
    /** The project a raw YAML save landed under, which a `name:` edit may have renamed. */
    val savedName: String? = null
)
