package cx.lpm.mobile.core.data.config

import cx.lpm.mobile.core.data.client.LpmClient
import cx.lpm.mobile.core.data.client.cloneProject
import cx.lpm.mobile.core.data.client.createProject
import cx.lpm.mobile.core.data.client.createSshProject
import cx.lpm.mobile.core.data.client.deleteAction
import cx.lpm.mobile.core.data.client.deleteProfile
import cx.lpm.mobile.core.data.client.deleteService
import cx.lpm.mobile.core.data.client.requestActionBody
import cx.lpm.mobile.core.data.client.requestConfig
import cx.lpm.mobile.core.data.client.requestDirs
import cx.lpm.mobile.core.data.client.requestProjects
import cx.lpm.mobile.core.data.client.requestServiceBody
import cx.lpm.mobile.core.data.client.requestSidebar
import cx.lpm.mobile.core.data.client.requestSshHosts
import cx.lpm.mobile.core.data.client.saveAction
import cx.lpm.mobile.core.data.client.saveConfig
import cx.lpm.mobile.core.data.client.saveProfile
import cx.lpm.mobile.core.data.client.saveService
import cx.lpm.mobile.core.data.session.GenerationTimeout
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.data.session.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject

/**
 * Adding projects and editing their configuration from the phone (iOS `AppModel`'s
 * config area): every request is guarded by a timeout so a silent Mac surfaces an
 * error rather than a spinner that never stops.
 */
class ProjectConfigStore(private val session: MacSession) : SessionStore {
    private val mutable = MutableStateFlow(ProjectConfigState())
    val state: StateFlow<ProjectConfigState> = mutable.asStateFlow()

    private val timeout = GenerationTimeout<String>(session.scope)
    private val client: LpmClient? get() = session.client

    override fun wire(client: LpmClient) {
        val c = client
        c.onListDirs = { listing, error ->
            timeout.cancel(DIRS)
            mutable.update {
                if (listing != null) {
                    it.copy(dirListingLoading = false, dirListing = listing, dirListingError = null)
                } else {
                    it.copy(dirListingLoading = false, dirListingError = error ?: NO_FOLDERS)
                }
            }
        }
        c.onListSshHosts = { hosts, error ->
            timeout.cancel(SSH_HOSTS)
            mutable.update {
                if (error != null) {
                    it.copy(sshHostsLoaded = true, sshHostsError = error)
                } else {
                    it.copy(sshHostsLoaded = true, sshHosts = hosts, sshHostsError = null)
                }
            }
        }
        c.onReadConfig = { _, _, content, available, error ->
            timeout.cancel(READ_CONFIG)
            mutable.update {
                if (error != null) {
                    it.copy(configTextLoading = false, configTextError = error)
                } else {
                    it.copy(
                        configTextLoading = false,
                        configText = content,
                        configAvailable = available,
                        configTextError = null
                    )
                }
            }
        }
        wireBodies(c)
        c.onCreateProject = { _, error -> finishMutation(error, c) }
        c.onCreateSshProject = { _, error -> finishMutation(error, c) }
        c.onCloneProject = { _, error -> finishMutation(error, c) }
        c.onSaveConfig = { _, _, name, error ->
            if (error == null && name.isNotEmpty()) mutable.update { it.copy(savedName = name) }
            finishMutation(error, c)
        }
        c.onSaveService = { _, _, error -> finishMutation(error, c) }
        c.onDeleteService = { _, _, error -> finishMutation(error, c) }
        c.onSaveProfile = { _, _, error -> finishMutation(error, c) }
        c.onDeleteProfile = { _, _, error -> finishMutation(error, c) }
        c.onSaveAction = { _, _, error -> finishMutation(error, c) }
        c.onDeleteAction = { _, _, error -> finishMutation(error, c) }
    }

    private fun wireBodies(c: LpmClient) {
        c.onServiceBody = { _, _, body, source, error ->
            timeout.cancel(SERVICE_BODY)
            mutable.update {
                if (error != null) {
                    it.copy(serviceBodyLoading = false, serviceBodyError = error)
                } else {
                    it.copy(
                        serviceBodyLoading = false,
                        serviceBody = body,
                        serviceBodySource = source
                    )
                }
            }
        }
        c.onActionBody = { _, _, body, section, source, error ->
            timeout.cancel(ACTION_BODY)
            mutable.update {
                if (error != null) {
                    it.copy(actionBodyLoading = false, actionBodyError = error)
                } else {
                    it.copy(
                        actionBodyLoading = false,
                        actionBody = body,
                        actionBodySection = section,
                        actionBodySource = source
                    )
                }
            }
        }
    }

    // The done-token survives a reset, so a sheet waiting on a write never takes an
    // older token for its own.
    override fun reset() {
        timeout.cancelAll()
        mutable.update { ProjectConfigState(mutationDoneToken = it.mutationDoneToken) }
    }

    /** One level of the Mac's folders for the folder browser; `~` is the Mac's home. */
    fun loadDirs(path: String) {
        mutable.update { it.copy(dirListingLoading = true, dirListingError = null) }
        client?.requestDirs(path)
        timeout.arm(DIRS, TIMEOUT_MS) {
            if (!state.value.dirListingLoading) return@arm
            mutable.update { it.copy(dirListingLoading = false, dirListingError = NO_REPLY) }
        }
    }

    /** The Mac's `~/.ssh/config` hosts, for the SSH project form. */
    fun loadSshHosts() {
        mutable.update { it.copy(sshHostsError = null) }
        client?.requestSshHosts()
        timeout.arm(SSH_HOSTS, TIMEOUT_MS) {
            if (state.value.sshHostsLoaded) return@arm
            mutable.update { it.copy(sshHostsLoaded = true, sshHostsError = NO_REPLY) }
        }
    }

    /** A config layer's raw text to seed the YAML editor; the old text goes first. */
    fun readConfig(project: String, layer: String) {
        mutable.update {
            it.copy(
                configText = null,
                configAvailable = true,
                configTextLoading = true,
                configTextError = null
            )
        }
        client?.requestConfig(project, layer)
        timeout.arm(READ_CONFIG, TIMEOUT_MS) {
            if (!state.value.configTextLoading) return@arm
            mutable.update { it.copy(configTextLoading = false, configTextError = NO_REPLY) }
        }
    }

    /** Fetch one service's mapping to seed the service form. */
    fun loadServiceBody(project: String, key: String) {
        mutable.update {
            it.copy(
                serviceBody = null,
                serviceBodySource = "",
                serviceBodyLoading = true,
                serviceBodyError = null
            )
        }
        client?.requestServiceBody(project, key)
        timeout.arm(SERVICE_BODY, TIMEOUT_MS) {
            if (!state.value.serviceBodyLoading) return@arm
            mutable.update { it.copy(serviceBodyLoading = false, serviceBodyError = NO_REPLY) }
        }
    }

    /** A new service form starts from nothing. */
    fun clearServiceBody() = mutable.update { it.copy(serviceBody = null, serviceBodySource = "") }

    fun dismissServiceBodyError() = mutable.update { it.copy(serviceBodyError = null) }

    /** Fetch one action's mapping to seed the action form; [key] may be `parent:child`. */
    fun loadActionBody(project: String, key: String) {
        mutable.update {
            it.copy(
                actionBody = null,
                actionBodySection = "actions",
                actionBodySource = "",
                actionBodyLoading = true,
                actionBodyError = null
            )
        }
        client?.requestActionBody(project, key)
        timeout.arm(ACTION_BODY, TIMEOUT_MS) {
            if (!state.value.actionBodyLoading) return@arm
            mutable.update { it.copy(actionBodyLoading = false, actionBodyError = NO_REPLY) }
        }
    }

    /** A new action form starts from nothing. */
    fun clearActionBody() = mutable.update { it.copy(actionBody = null) }

    fun dismissActionBodyError() = mutable.update { it.copy(actionBodyError = null) }

    /** A new project, or an existing folder adopted, rooted at [root]. */
    fun createProject(name: String, root: String) {
        beginMutation()
        client?.createProject(name, root)
    }

    /** A project on an SSH host; [ssh] is the form's `host`/`user`/`port`/`key`/`dir`. */
    fun createSshProject(name: String, ssh: JsonObject) {
        beginMutation()
        client?.createSshProject(name, ssh)
    }

    /** A clone of [url] into `destParent/name`; the network can take a while, so it waits longer. */
    fun cloneProject(name: String, url: String, branch: String, destParent: String) {
        beginMutation(CLONE_TIMEOUT_MS)
        client?.cloneProject(name, url, branch, destParent)
    }

    /** Write a layer's raw text; the reply names the project, renamed by a `name:` edit. */
    fun saveConfig(project: String, layer: String, content: String) {
        mutable.update { it.copy(savedName = null) }
        beginMutation()
        client?.saveConfig(project, layer, content)
    }

    /**
     * Create or update a service; a [previousKey] that differs renames it and
     * rewrites every reference. [payload] is the form's managed fields.
     */
    fun saveService(project: String, key: String, payload: JsonObject, previousKey: String?) {
        beginMutation()
        client?.saveService(project, key, payload, previousKey)
    }

    fun deleteService(project: String, key: String) {
        beginMutation()
        client?.deleteService(project, key)
    }

    /** Create or update a profile's ordered [services]; a [previousName] that differs renames it. */
    fun saveProfile(project: String, name: String, services: List<String>, previousName: String?) {
        beginMutation()
        client?.saveProfile(project, name, services, previousName)
    }

    fun deleteProfile(project: String, name: String) {
        beginMutation()
        client?.deleteProfile(project, name)
    }

    /**
     * Create or update an action from the form's full [payload]; a [previousKey] that
     * differs renames it, keeping its [section].
     */
    fun saveAction(
        project: String,
        key: String,
        payload: JsonObject,
        previousKey: String?,
        section: String?
    ) {
        beginMutation()
        client?.saveAction(project, key, payload, previousKey, section)
    }

    fun deleteAction(project: String, key: String) {
        beginMutation()
        client?.deleteAction(project, key)
    }

    fun dismissMutationError() = mutable.update { it.copy(mutationError = null) }

    private fun beginMutation(millis: Long = TIMEOUT_MS) {
        mutable.update { it.copy(mutationInFlight = true, mutationError = null) }
        timeout.arm(MUTATION, millis) {
            if (!state.value.mutationInFlight) return@arm
            mutable.update { it.copy(mutationInFlight = false, mutationError = NO_REPLY) }
        }
    }

    // A write's reply: drop the spinner, then surface the error or dismiss the sheet
    // and refresh the list so the edit shows at once.
    private fun finishMutation(error: String?, c: LpmClient) {
        timeout.cancel(MUTATION)
        if (error != null) {
            mutable.update { it.copy(mutationInFlight = false, mutationError = error) }
            return
        }
        mutable.update {
            it.copy(mutationInFlight = false, mutationDoneToken = it.mutationDoneToken + 1)
        }
        c.requestProjects()
        c.requestSidebar()
    }

    private companion object {
        const val DIRS = "dirs"
        const val SSH_HOSTS = "sshHosts"
        const val READ_CONFIG = "readConfig"
        const val SERVICE_BODY = "serviceBody"
        const val ACTION_BODY = "actionBody"
        const val MUTATION = "mutation"
        const val TIMEOUT_MS = 20_000L
        const val CLONE_TIMEOUT_MS = 120_000L
        const val NO_REPLY = "The Mac didn't respond. Try again."
        const val NO_FOLDERS = "Couldn't list folders."
    }
}
