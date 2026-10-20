package app.awn.quran.feature.read.settings

import app.awn.quran.core.data.ScriptId
import app.awn.quran.feature.read.R

internal fun ScriptId.next(): ScriptId = ScriptId.entries[(ordinal + 1) % ScriptId.entries.size]

internal fun TextSize.next(): TextSize = TextSize.entries[(ordinal + 1) % TextSize.entries.size]

internal fun ScriptId.labelRes(): Int =
    when (this) {
        ScriptId.QpcHafs -> R.string.script_qpc_hafs
        ScriptId.V1 -> R.string.script_v1
        ScriptId.V2 -> R.string.script_v2
        ScriptId.V4Tajweed -> R.string.script_v4_tajweed
        ScriptId.IndoPak -> R.string.script_indopak
    }

internal fun PageView.labelRes(): Int =
    when (this) {
        PageView.Mushaf -> R.string.action_mushaf
        PageView.AyahList -> R.string.action_ayah_list
    }

internal fun TextSize.labelRes(): Int =
    when (this) {
        TextSize.Small -> R.string.text_size_small
        TextSize.Medium -> R.string.text_size_medium
        TextSize.Large -> R.string.text_size_large
    }
