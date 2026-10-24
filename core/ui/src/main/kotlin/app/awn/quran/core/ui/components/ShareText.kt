package app.awn.quran.core.ui.components

import android.content.Context
import android.content.Intent

/** Opens the Android share sheet, titled [chooserTitle], with [text] as the shared plain text. */
fun shareText(context: Context, text: String, chooserTitle: String) {
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    val chooser = Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}
