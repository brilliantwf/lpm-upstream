package cx.lpm.mobile.feature.automations.ui

import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.component.ListDefaults

/**
 * Above a list whose first section has no header: iOS keeps an empty header's room
 * there (measured on the automation page and the job editor).
 */
val HeaderlessTopPadding = ListDefaults.ContentTopPadding + 18.dp
