package app.awn.quran.update

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

/**
 * Hands the downloaded APK to the system installer. When Android has not yet allowed installs from
 * Awn, opens that permission screen instead; the user taps Install again after allowing it, so the
 * row stays in its Ready state.
 */
internal fun installApk(context: Context, apkFile: File) {
    if (!context.packageManager.canRequestPackageInstalls()) {
        val permissionScreen =
            Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    "package:${context.packageName}".toUri(),
                )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(permissionScreen)
        return
    }
    val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
    val installScreen =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(apkUri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(installScreen)
}
