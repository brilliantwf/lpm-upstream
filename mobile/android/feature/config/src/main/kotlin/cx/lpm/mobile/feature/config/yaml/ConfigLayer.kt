package cx.lpm.mobile.feature.config.yaml

import androidx.annotation.StringRes
import cx.lpm.mobile.feature.config.R

/** A configuration layer: each is its own file on the Mac. */
enum class ConfigLayer(val wire: String, @param:StringRes val title: Int) {
    Project("project", R.string.config_layer_project),
    Repo("repo", R.string.config_layer_repo),
    Global("global", R.string.config_layer_global)
}
