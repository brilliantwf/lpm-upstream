package cx.lpm.mobile.core.model

enum class AppearanceMode(val rawValue: String, val label: String) {
    System("system", "System"),
    Light("light", "Light"),
    Dark("dark", "Dark")
    ;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        System -> systemDark
        Light -> false
        Dark -> true
    }

    companion object {
        const val STORAGE_KEY = "appearanceMode"

        fun of(rawValue: String?): AppearanceMode =
            entries.firstOrNull { it.rawValue == rawValue } ?: System
    }
}
