package app.awn.quran.feature.read.quran

import app.awn.quran.core.data.ScriptId
import app.awn.quran.feature.read.page.indoPakScripts
import app.awn.quran.feature.read.settings.ReadSettings

/**
 * The print the reader can open with [installedPackIds] on the phone. A Madani print without its
 * font is still readable (the page draws the bundled Hafs text), but the IndoPak print takes its
 * page layout from a downloaded pack, so without that pack its pages cannot be read at all and the
 * default print stands in. Version 0.1.0 saved the IndoPak print before it was downloaded.
 */
internal fun readablePrintOf(script: ScriptId, installedPackIds: Set<String>): ScriptId =
    if (script in indoPakScripts && script.packId !in installedPackIds) {
        ReadSettings().script
    } else {
        script
    }
