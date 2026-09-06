package io.github.cespresso.clumo.domain

/**
 * What to call a device. Every surface that names one -- the list, the device screen, the
 * appearance editor, the widgets -- has to answer this from the same three sources, and a
 * device renamed in one place has to read the same in all of them.
 */
object DeviceNaming {

    /** A device that has told us nothing yet is still called something. */
    const val DEFAULT = "CLumo"

    private const val PREFIX = "CLumo-"

    /**
     * A name is CLumo's own only if it looks like one. A phone stores whatever GAP name it
     * read over GATT against the bond and hands that back on every reconnect, so firmware
     * that left the name at NimBLE's default is remembered as "nimble". Dropping such a
     * name lets the one derived from the device id stand instead.
     */
    fun ownName(raw: String?): String? = raw?.takeIf { it.startsWith(PREFIX) }

    /**
     * The name the user gave it, else the name it advertises, else the name it was stored
     * under. [scannedName] outranks [fallbackName] because it comes from the live link, and
     * an alias is only reachable once the device has an id to file one against.
     *
     * A stored alias is never blank: AppPreferences.setAlias drops an empty rename rather
     * than saving one, so clearing the field restores the device's own name here.
     */
    fun displayName(
        deviceId: String?,
        aliases: Map<String, String>,
        scannedName: String? = null,
        fallbackName: String? = null,
    ): String =
        deviceId?.let { aliases[it] }
            ?: scannedName
            ?: fallbackName
            ?: DEFAULT
}
