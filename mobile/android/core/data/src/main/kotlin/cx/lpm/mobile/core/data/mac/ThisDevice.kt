package cx.lpm.mobile.core.data.mac

/**
 * The device lpm Link runs on: the name it pairs under, and what copy calls it
 * ("phone" where iOS says "iPhone"/"iPad", an allowed platform difference).
 */
data class ThisDevice(val name: String, val noun: String = "phone")
