package cx.lpm.mobile.feature.automations.editor

import cx.lpm.mobile.core.model.AutomationJob

/**
 * Whether the editor creates a job or edits one. A new job always goes to the global
 * layer (like the desktop's "New job"), starting in [Create.project] when opened from
 * a project; an edit carries the job so the sheet knows its layer, project and id.
 */
sealed interface AutomationEditorTarget {
    data class Create(val project: String? = null) : AutomationEditorTarget

    data class Edit(val job: AutomationJob) : AutomationEditorTarget
}
